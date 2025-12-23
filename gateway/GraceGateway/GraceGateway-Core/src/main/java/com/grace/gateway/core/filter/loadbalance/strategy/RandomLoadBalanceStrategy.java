package com.grace.gateway.core.filter.loadbalance.strategy;

import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.core.context.GatewayContext;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.grace.gateway.common.constant.LoadBalanceConstant.RANDOM_LOAD_BALANCE_STRATEGY;
/**
 * 随机负载均衡策略
 * 从可用服务实例中随机选择一个实例处理请求，适用于无状态服务的负载均衡
 * 实现简单且在实例性能相近时能达到较好的负载分散效果
 */
public class RandomLoadBalanceStrategy implements LoadBalanceStrategy {
    /**
     * 从实例列表中随机选择一个服务实例
     * 核心逻辑：使用线程安全的随机数生成器，在实例列表范围内随机选择索引
     *
     * @param context  网关上下文对象（本策略无需使用上下文信息）
     * @param instances 可用的服务实例列表（已过滤出健康实例）
     * @return 随机选中的服务实例
     */
    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        // 1. 使用ThreadLocalRandom获取当前线程的随机数生成器（线程安全，性能优于Random）
        // 2. 生成0到实例列表大小-1之间的随机整数作为索引
        // 3. 根据索引从实例列表中返回对应的实例
        return instances.get(ThreadLocalRandom.current().nextInt(instances.size()));
    }
    /**
     * 获取当前负载均衡策略的标识
     * 用于策略管理器注册和选择策略时识别
     *
     * @return 策略标识，固定为RANDOM_LOAD_BALANCE_STRATEGY
     */
    @Override
    public String mark() {
        return RANDOM_LOAD_BALANCE_STRATEGY;
    }
}
