package com.grace.gateway.core.filter.loadbalance.strategy;

import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.config.util.FilterUtil;
import com.grace.gateway.core.algorithm.ConsistentHashing;
import com.grace.gateway.core.context.GatewayContext;

import java.util.List;

import static com.grace.gateway.common.constant.FilterConstant.LOAD_BALANCE_FILTER_NAME;
import static com.grace.gateway.common.constant.LoadBalanceConstant.CLIENT_IP_CONSISTENT_HASH_LOAD_BALANCE_STRATEGY;

/**
 * 基于客户端IP的一致性哈希负载均衡策略
 * 通过一致性哈希算法，将同一客户端IP的请求稳定路由到同一服务实例
 * 适用于对会话一致性要求高的场景（如带状态服务），同时在实例扩缩容时减少流量抖动
 */
public class ClientIpConsistentHashLoadBalanceStrategy implements LoadBalanceStrategy {
    /**
     * 从服务实例列表中选择目标实例
     * 核心逻辑：基于客户端IP的哈希值和一致性哈希算法实现稳定路由
     *
     * @param context  网关上下文对象，包含当前请求信息（如客户端IP）
     * @param instances 可用的服务实例列表（非灰度实例，已通过前置过滤）
     * @return 选中的服务实例，若未找到匹配则返回列表第一个实例
     */
    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        // 从路由配置中获取负载均衡过滤器的详细配置
        RouteDefinition.LoadBalanceFilterConfig loadBalanceFilterConfig = FilterUtil.findFilterConfigByClass(
                context.getRoute().getFilterConfigs(), LOAD_BALANCE_FILTER_NAME, RouteDefinition.LoadBalanceFilterConfig.class);
        // 配置虚拟节点数量（默认为1，配置有效时使用配置值）
        // 虚拟节点用于优化一致性哈希的负载均衡效果，避免实例分布不均
        int virtualNodeNum = 1;
        if (loadBalanceFilterConfig != null && loadBalanceFilterConfig.getVirtualNodeNum() > 0) {
            virtualNodeNum = loadBalanceFilterConfig.getVirtualNodeNum();
        }
        // 提取所有实例的唯一标识（instanceId）作为哈希环上的物理节点
        List<String> nodes = instances.stream()
                .map(ServiceInstance::getInstanceId)
                .toList();
        // 初始化一致性哈希算法实例，传入物理节点和虚拟节点数量
        ConsistentHashing consistentHashing = new ConsistentHashing(nodes, virtualNodeNum);
        // 基于客户端IP的哈希值作为键，从哈希环上获取对应的节点（instanceId）
        // 客户端IP哈希值确保同一IP的请求映射到同一键，进而路由到同一实例
        String selectedNode = consistentHashing.getNode(String.valueOf(context.getRequest().getHost().hashCode()));
        // 遍历实例列表，找到与选中节点（instanceId）匹配的服务实例
        for (ServiceInstance instance : instances) {
            if (instance.getInstanceId().equals(selectedNode)) {
                return instance;
            }
        }
        // 若未找到匹配实例（理论上不会发生，除非实例列表在计算过程中变化），返回第一个实例作为降级方案
        return instances.get(0);
    }
    /**
     * 获取当前负载均衡策略的标识
     * 用于策略管理器注册和选择策略时使用
     *
     * @return 策略标识，固定为CLIENT_IP_CONSISTENT_HASH_LOAD_BALANCE_STRATEGY
     */
    @Override
    public String mark() {
        return CLIENT_IP_CONSISTENT_HASH_LOAD_BALANCE_STRATEGY;
    }
}
