package com.grace.gateway.core.filter.gray;

import cn.hutool.json.JSONUtil;
import com.grace.gateway.config.manager.DynamicConfigManager;
import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.config.util.FilterUtil;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.Filter;
import com.grace.gateway.core.filter.gray.strategy.GrayStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.grace.gateway.common.constant.FilterConstant.GRAY_FILTER_NAME;
import static com.grace.gateway.common.constant.FilterConstant.GRAY_FILTER_ORDER;

/**
 * 灰度发布过滤器
 * 负责在请求路由前判断是否需要将请求转发到灰度实例
 * 实现了网关的Filter接口，作为网关过滤链的一部分工作
 */
@Slf4j
public class GrayFilter implements Filter {

    /**
     * 前置过滤方法，在请求被路由到目标服务前执行
     * 主要逻辑：检查灰度配置，选择灰度策略，判断是否路由到灰度实例
     *
     * @param context 网关上下文对象，包含当前请求的所有信息和处理状态
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 从路由定义中查找灰度过滤器的配置
        RouteDefinition.FilterConfig filterConfig = FilterUtil.findFilterConfigByName(
                context.getRoute().getFilterConfigs(), GRAY_FILTER_NAME);
        // 如果没有配置，使用默认配置
        if (filterConfig == null) {
            filterConfig = FilterUtil.buildDefaultGrayFilterConfig();
        }
        // 如果灰度功能未启用，直接返回，不进行后续处理
        if (!filterConfig.isEnable()) {
            return;
        }
        // 从动态配置管理器中获取当前服务的所有实例（包括灰度和非灰度）
        List<ServiceInstance> instances = DynamicConfigManager.getInstance()
                .getInstancesByServiceName(context.getRequest().getServiceDefinition().getServiceName())
                .values().stream().toList();
        // 检查是否存在可用的灰度实例
        if (instances.stream().anyMatch(instance -> instance.isEnabled() && instance.isGray())) {
            // 存在灰度实例，根据配置选择对应的灰度策略
            GrayStrategy strategy = selectGrayStrategy(
                    JSONUtil.toBean(filterConfig.getConfig(), RouteDefinition.GrayFilterConfig.class));
            // 使用选定的策略判断当前请求是否应该路由到灰度实例，并设置到请求中
            context.getRequest().setGray(strategy.shouldRoute2Gray(context, instances));
        } else {
            // 不存在可用的灰度实例，设置为不路由到灰度
            context.getRequest().setGray(false);
        }
        // 继续执行过滤链
        context.doFilter();
    }
    /**
     * 后置过滤方法，在请求处理完成后执行
     * 灰度发布主要在路由前决策，因此后置处理仅继续过滤链
     *
     * @param context 网关上下文对象
     */
    @Override
    public void doPostFilter(GatewayContext context) {
        // 继续执行过滤链
        context.doFilter();
    }
    /**
     * 获取当前过滤器的标识
     * 用于过滤器的注册和识别
     *
     * @return 过滤器的唯一标识
     */
    @Override
    public String mark() {
        return GRAY_FILTER_NAME;
    }
    /**
     * 获取过滤器的执行顺序
     * 决定了当前过滤器在过滤链中的执行位置
     *
     * @return 过滤器的执行顺序值，值越小越先执行
     */
    @Override
    public int getOrder() {
        return GRAY_FILTER_ORDER;
    }
    /**
     * 选择灰度策略
     * 根据灰度过滤器配置中的策略名称，从策略管理器中获取对应的策略实例
     *
     * @param grayFilterConfig 灰度过滤器的详细配置
     * @return 选中的灰度策略实例
     */
    private GrayStrategy selectGrayStrategy(RouteDefinition.GrayFilterConfig grayFilterConfig) {
        return GrayStrategyManager.getStrategy(grayFilterConfig.getStrategyName());
    }
}
