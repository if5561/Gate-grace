package com.grace.gateway.core.filter.loadbalance.strategy;

import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.core.context.GatewayContext;

import java.util.List;

import static com.grace.gateway.common.constant.LoadBalanceConstant.GRAY_LOAD_BALANCE_STRATEGY;

/**
 * 灰度负载均衡策略
 * 专门用于灰度实例的负载均衡选择，结合实例阈值（权重）和客户端IP哈希实现流量分配
 * 确保同一客户端IP的请求始终路由到同一灰度实例，同时按阈值比例分配流量
 */
public class GrayLoadBalanceStrategy implements LoadBalanceStrategy {
    /**
     * 从灰度实例列表中选择目标实例
     * 核心逻辑：基于客户端IP哈希和实例阈值（权重）实现带权重的一致性路由
     *
     * @param context  网关上下文对象，包含客户端IP等请求信息
     * @param instances 可用的灰度实例列表（已筛选出启用且标记为灰度的实例）
     * @return 选中的灰度实例，若无法选择则返回null
     */
    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        // 计算所有灰度实例的总阈值（转换为整数，放大100倍避免浮点精度问题）
        // 总阈值代表所有灰度实例可承载的总流量比例
        int totalThreshold = (int) (instances.stream().mapToDouble(ServiceInstance::getThreshold).sum() * 100);
        // 若总阈值为0，说明没有可用的灰度实例（或阈值配置无效），返回null
        if (totalThreshold <= 0) return null;
        // 基于客户端IP的哈希值计算"流量落点"：
        // 1. 获取客户端IP的哈希值并取绝对值，确保为正数
        // 2. 对总阈值取模，得到0~totalThreshold-1之间的数值，作为流量分配的基准
        int randomThreshold = Math.abs(context.getRequest().getHost().hashCode()) % totalThreshold;
        // 遍历灰度实例，根据实例阈值分配流量：
        // 每个实例的阈值对应一个"流量区间"，当randomThreshold落入某个区间时，选择该实例
        // 例如：实例A阈值0.3（30）、实例B阈值0.2（20），总阈值50
        // 若randomThreshold=25，落入A的区间（0~29），则选择A
        for (ServiceInstance instance : instances) {
            // 将当前实例的阈值（放大100倍）从随机阈值中减去
            randomThreshold -= instance.getThreshold() * 100;
            // 当随机阈值小于0时，说明当前实例是"流量落点"，返回该实例
            if (randomThreshold < 0) return instance;
        }
        // 若遍历完所有实例仍未找到（理论上不会发生，除非计算错误），返回null
        return null;
    }
    /**
     * 获取当前策略的唯一标识
     * 用于策略管理器注册和选择策略时识别，固定为灰度负载均衡策略标识
     *
     * @return 策略标识，固定为GRAY_LOAD_BALANCE_STRATEGY
     */
    @Override
    public String mark() {
        return GRAY_LOAD_BALANCE_STRATEGY;
    }
}
