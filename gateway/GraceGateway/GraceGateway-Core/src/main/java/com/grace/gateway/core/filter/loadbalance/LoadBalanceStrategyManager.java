package com.grace.gateway.core.filter.loadbalance;

import com.grace.gateway.core.filter.loadbalance.strategy.LoadBalanceStrategy;
import com.grace.gateway.core.filter.loadbalance.strategy.RoundRobinLoadBalanceStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * 负载均衡负载均衡策略管理器
 * 负责加载和管理所有可用的负载均衡策略，提供策略获取的接口
 */
@Slf4j
public class LoadBalanceStrategyManager {

    /**
     * 存储负载均衡策略的映射表
     * key: 策略标识(由mark()方法返回)
     * value: 对应的负载均衡策略实例
     */
    private static final Map<String, LoadBalanceStrategy> strategyMap = new HashMap<>();

    static {
        // 使用Java的ServiceLoader机制加载所有实现了LoadBalanceStrategy接口的服务
        // 这是一种服务发现机制，实现了解耦，无需硬编码实例化具体策略类
        ServiceLoader<LoadBalanceStrategy> serviceLoader = ServiceLoader.load(LoadBalanceStrategy.class);
        // 遍历所有加载到的策略实例，存入映射表中
        for (LoadBalanceStrategy strategy : serviceLoader) {
            // 以策略的标识作为key，策略实例作为value存入map
            strategyMap.put(strategy.mark(), strategy);
            // 记录日志，提示策略加载成功
            log.info("load loadbalance strategy success: {}", strategy);
        }
    }

    /**
     * 根据策略名称获取对应的负载均衡策略实例
     * @param name 策略名称(对应mark()方法返回的标识)
     * @return 对应的负载均衡策略实例，如果未找到则返回默认的轮询策略
     */
    public static LoadBalanceStrategy getStrategy(String name) {
        // 从映射表中获取指定名称的策略
        LoadBalanceStrategy strategy = strategyMap.get(name);
        // 如果未找到对应策略，则使用轮询策略作为默认策略
        if (strategy == null)
            strategy = new RoundRobinLoadBalanceStrategy();
        return strategy;
    }

}
