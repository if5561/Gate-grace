package com.grace.gateway.core.filter.loadbalance.strategy;

import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.core.context.GatewayContext;

import java.util.List;

/**
 * 负载均衡策略接口
 * 定义了所有负载均衡策略必须实现的标准方法，采用策略模式设计
 * 不同的负载均衡算法（如轮询、随机、一致性哈希等）需实现此接口
 */
public interface LoadBalanceStrategy {
    /**
     * 从可用服务实例中选择一个目标实例
     * 核心方法，不同实现类会根据各自的负载均衡算法返回不同的实例
     *
     * @param context  网关上下文对象，包含当前请求的所有信息（如客户端IP、请求参数等）
     * @param instances 可用的服务实例列表（已过滤掉不可用实例）
     * @return 选中的服务实例，用于处理当前请求
     */
    ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances);
    /**
     * 获取当前负载均衡策略的唯一标识
     * 用于策略的注册、识别和选择，在配置中指定负载均衡策略时会用到此标识
     *
     * @return 策略的标识字符串（如"round_robin"、"consistent_hash"等）
     */
    String mark();


}
