package com.grace.gateway.core.filter.gray.strategy;

import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.config.util.FilterUtil;
import com.grace.gateway.core.context.GatewayContext;

import java.util.List;

import static com.grace.gateway.common.constant.FilterConstant.GRAY_FILTER_NAME;
import static com.grace.gateway.common.constant.GrayConstant.MAX_GRAY_THRESHOLD;
import static com.grace.gateway.common.constant.GrayConstant.THRESHOLD_GRAY_STRATEGY;

/**
 * 基于流量比例的灰度路由策略实现
 * 通过随机数与灰度阈值比较，按比例将请求路由到灰度实例或正常实例
 * 适用于需要按固定比例分配流量的灰度发布场景（如10%流量到灰度版本）
 */
public class ThresholdGrayStrategy implements GrayStrategy {

    /**
     * 判断当前请求是否应该路由到灰度实例
     * 核心逻辑：基于随机数和灰度阈值的比较决定路由方向，实现流量的比例分配
     *
     * @param context  网关上下文对象，包含当前请求的详细信息
     * @param instances 服务的所有实例列表（包括灰度和非灰度实例）
     * @return true-路由到灰度实例，false-路由到正常实例
     */
    @Override
    public boolean shouldRoute2Gray(GatewayContext context, List<ServiceInstance> instances) {
        // 检查是否存在可用的非灰度实例（如果没有，则默认路由到灰度实例）
        if (instances.stream().anyMatch(instance -> instance.isEnabled() && !instance.isGray())) {
            // 从路由配置中获取灰度过滤器的详细配置
            RouteDefinition.GrayFilterConfig grayFilterConfig = FilterUtil.findFilterConfigByClass(
                    context.getRoute().getFilterConfigs(), GRAY_FILTER_NAME, RouteDefinition.GrayFilterConfig.class);

            // 确定最大灰度阈值：如果配置不存在则使用默认值，否则使用配置值
            double maxGrayThreshold = grayFilterConfig == null ? MAX_GRAY_THRESHOLD : grayFilterConfig.getMaxGrayThreshold();

            // 计算总灰度阈值：所有实例的阈值之和（单个实例阈值代表该实例可承担的灰度流量比例）
            double grayThreshold = instances.stream().mapToDouble(ServiceInstance::getThreshold).sum();

            // 确保总灰度阈值不超过最大限制（防止流量分配超过系统承载能力）
            grayThreshold = Math.min(grayThreshold, maxGrayThreshold);

            // 核心算法：
            // 1. 生成[0,1)之间的随机数，通过Math.abs(Math.random() - 1)转换为(0,1]（效果等同于直接使用Math.random()）
            // 2. 如果随机数小于等于灰度阈值（如0.3），则路由到灰度实例（即30%概率）
            // 此逻辑可简化为：return Math.random() <= grayThreshold;
            return Math.abs(Math.random() - 1) <= grayThreshold;
        }
        // 当没有可用的非灰度实例时，默认路由到灰度实例（保证服务可用性）
        return true;
    }

    /**
     * 获取当前策略的唯一标识
     * 用于策略管理器注册策略和根据配置选择策略时使用
     *
     * @return 策略标识，固定为THRESHOLD_GRAY_STRATEGY
     */
    @Override
    public String mark() {
        return THRESHOLD_GRAY_STRATEGY;
    }

}
