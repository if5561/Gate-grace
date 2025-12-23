package com.grace.gateway.core.filter.loadbalance;

import cn.hutool.json.JSONUtil;
import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.common.exception.NotFoundException;
import com.grace.gateway.config.manager.DynamicConfigManager;
import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.config.util.FilterUtil;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.Filter;
import com.grace.gateway.core.filter.loadbalance.strategy.GrayLoadBalanceStrategy;
import com.grace.gateway.core.filter.loadbalance.strategy.LoadBalanceStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.grace.gateway.common.constant.FilterConstant.LOAD_BALANCE_FILTER_NAME;
import static com.grace.gateway.common.constant.FilterConstant.LOAD_BALANCE_FILTER_ORDER;

/**
 * 负载均衡过滤器
 * 负责在请求路由阶段从可用服务实例中选择一个合适的实例处理请求
 * 支持灰度请求与非灰度请求的差异化负载均衡策略
 */
@Slf4j
public class LoadBalanceFilter implements Filter {
    /**
     * 前置过滤方法，在请求路由前执行负载均衡逻辑
     * 核心职责：根据请求类型（灰度/非灰度）选择对应的负载均衡策略，筛选可用实例并完成实例选择
     *
     * @param context 网关上下文对象，包含当前请求的所有信息和处理状态
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 从路由配置中查找负载均衡过滤器的配置
        RouteDefinition.FilterConfig filterConfig = FilterUtil.findFilterConfigByName(
                context.getRoute().getFilterConfigs(), LOAD_BALANCE_FILTER_NAME);
        // 如果没有配置，使用默认的负载均衡配置
        if (filterConfig == null) {
            filterConfig = FilterUtil.buildDefaultLoadBalanceFilterConfig();
        }
        // 从动态配置管理器中获取当前服务的所有实例
        List<ServiceInstance> instances = DynamicConfigManager.getInstance()
                .getInstancesByServiceName(context.getRequest().getServiceDefinition().getServiceName())
                .values().stream().toList();
        LoadBalanceStrategy strategy; // 负载均衡策略实例
        if (context.getRequest().isGray()) {
            // 灰度请求：使用灰度专用负载均衡策略
            strategy = new GrayLoadBalanceStrategy();
            // 筛选可用的灰度实例（必须启用且标记为灰度）
            instances = instances.stream()
                    .filter(instance -> instance.isEnabled() && instance.isGray())
                    .toList();
        } else {
            // 非灰度请求：根据配置选择对应的负载均衡策略
            strategy = selectLoadBalanceStrategy(
                    JSONUtil.toBean(filterConfig.getConfig(), RouteDefinition.LoadBalanceFilterConfig.class));
        }
        // 如果没有可用实例，抛出服务实例未找到异常
        if (instances.isEmpty()) {
            throw new NotFoundException(ResponseCode.SERVICE_INSTANCE_NOT_FOUND);
        }
        // 使用选定的策略从可用实例中选择一个目标实例
        ServiceInstance serviceInstance = strategy.selectInstance(context, instances);
        if (null == serviceInstance) {
            // 若策略未选中实例，同样抛出服务实例未找到异常
            throw new NotFoundException(ResponseCode.SERVICE_INSTANCE_NOT_FOUND);
        }
        // 将选中的实例地址（IP:端口）设置到请求中，用于后续路由
        context.getRequest().setModifyHost(serviceInstance.getIp() + ":" + serviceInstance.getPort());
        // 继续执行过滤链
        context.doFilter();
    }
    /**
     * 后置过滤方法，在请求处理完成后执行
     * 负载均衡主要在路由前决策，因此后置处理仅继续过滤链
     *
     * @param context 网关上下文对象
     */
    @Override
    public void doPostFilter(GatewayContext context) {
        context.doFilter();
    }
    /**
     * 获取当前过滤器的标识
     * 用于过滤器的注册和识别
     *
     * @return 过滤器的唯一标识（LOAD_BALANCE_FILTER_NAME）
     */
    @Override
    public String mark() {
        return LOAD_BALANCE_FILTER_NAME;
    }
    /**
     * 获取过滤器的执行顺序
     * 决定了当前过滤器在过滤链中的执行位置（需在灰度过滤之后、路由之前）
     *
     * @return 过滤器的执行顺序值（LOAD_BALANCE_FILTER_ORDER）
     */
    @Override
    public int getOrder() {
        return LOAD_BALANCE_FILTER_ORDER;
    }
    /**
     * 选择非灰度请求的负载均衡策略
     * 根据负载均衡过滤器配置中的策略名称，从策略管理器中获取对应的策略实例
     *
     * @param loadBalanceFilterConfig 负载均衡过滤器的详细配置
     * @return 选中的负载均衡策略实例
     */
    private LoadBalanceStrategy selectLoadBalanceStrategy(RouteDefinition.LoadBalanceFilterConfig loadBalanceFilterConfig) {
        return LoadBalanceStrategyManager.getStrategy(loadBalanceFilterConfig.getStrategyName());
    }
}
