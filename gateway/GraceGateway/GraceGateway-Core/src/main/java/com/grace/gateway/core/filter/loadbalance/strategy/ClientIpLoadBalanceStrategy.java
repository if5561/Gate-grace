package com.grace.gateway.core.filter.loadbalance.strategy;

import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.core.context.GatewayContext;

import java.util.List;

import static com.grace.gateway.common.constant.LoadBalanceConstant.CLIENT_IP_LOAD_BALANCE_STRATEGY;
/**
 * 基于客户端IP的负载均衡策略
 * 通过客户端IP的哈希值实现请求与服务实例的绑定，确保同一IP的请求始终路由到同一实例
 * 适用于需要会话一致性的场景，但在实例数量变化时可能导致路由抖动
 */
public class ClientIpLoadBalanceStrategy implements LoadBalanceStrategy {
    /**
     * 根据客户端IP选择目标服务实例
     * 核心逻辑：通过IP哈希值与实例数量取模，实现固定IP到固定实例的映射
     *
     * @param context  网关上下文对象，包含当前请求的客户端IP信息
     * @param instances 可用的服务实例列表（已过滤出健康实例）
     * @return 选中的服务实例，确保同一IP始终返回相同实例（实例数量不变时）
     */
    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        // 1. 获取客户端IP的哈希值并取绝对值（避免负数）
        // 2. 与实例列表大小取模，得到一个0~(size-1)的索引
        // 3. 根据索引从实例列表中选择目标实例
        // 此算法保证：在实例数量不变时，同一IP始终路由到同一实例
        return instances.get(Math.abs(context.getRequest().getHost().hashCode()) % instances.size());
    }
    /**
     * 获取当前负载均衡策略的标识
     * 用于策略管理器注册和选择策略时使用
     *
     * @return 策略标识，固定为CLIENT_IP_LOAD_BALANCE_STRATEGY
     */
    @Override
    public String mark() {
        return CLIENT_IP_LOAD_BALANCE_STRATEGY;
    }

}
