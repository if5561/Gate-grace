package com.grace.gateway.core.filter.gray.strategy;

import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.config.util.FilterUtil;
import com.grace.gateway.core.context.GatewayContext;

import java.util.List;

import static com.grace.gateway.common.constant.FilterConstant.GRAY_FILTER_NAME;
import static com.grace.gateway.common.constant.GrayConstant.CLIENT_IP_GRAY_STRATEGY;

/**
 * 基于客户端IP的灰度路由策略实现
 * 根据客户端IP的哈希值进行灰度决策，确保同一IP的请求始终路由到相同类型的实例（灰度或非灰度）
 */
public class ClientIpGrayStrategy implements GrayStrategy {
    /**
     * 判断当前请求是否应该路由到灰度实例
     * 核心逻辑：基于客户端IP的哈希值与灰度阈值比较决定路由方向
     *
     * @param context  网关上下文对象，包含当前请求信息
     * @param instances 服务的所有实例列表（包括灰度和非灰度）
     * @return true-路由到灰度实例，false-路由到正常实例
     */
    @Override
    public boolean shouldRoute2Gray(GatewayContext context, List<ServiceInstance> instances) {
        // 检查是否存在可用的非灰度实例
        if (instances.stream().anyMatch(instance -> instance.isEnabled() && !instance.isGray())) {
            // 获取灰度过滤器的配置
            RouteDefinition.GrayFilterConfig grayFilterConfig = FilterUtil.findFilterConfigByClass(
                    context.getRoute().getFilterConfigs(), GRAY_FILTER_NAME, RouteDefinition.GrayFilterConfig.class);
            // 计算所有实例的阈值总和作为基础灰度比例
            double grayThreshold = instances.stream().mapToDouble(ServiceInstance::getThreshold).sum();
            // 确保灰度比例不超过配置的最大阈值
            grayThreshold = Math.min(grayThreshold, grayFilterConfig.getMaxGrayThreshold());
            // 核心算法：
            // 1. 获取客户端IP的哈希值并取绝对值
            // 2. 对100取模，将哈希值映射到0-99的范围
            // 3. 与灰度阈值（转换为百分比）比较，决定是否路由到灰度实例
            // 这种方式保证了相同IP的请求会被一致地路由（要么始终灰度，要么始终正常）
            return Math.abs(context.getRequest().getHost().hashCode()) % 100 <= grayThreshold * 100;
        }
        // 如果没有可用的非灰度实例，默认路由到灰度实例
        return true;
    }
    /**
     * 获取当前策略的标识
     * 用于策略管理器注册和选择策略时使用
     *
     * @return 策略标识，固定为CLIENT_IP_GRAY_STRATEGY
     */
    @Override
    public String mark() {
        return CLIENT_IP_GRAY_STRATEGY;
    }

}
