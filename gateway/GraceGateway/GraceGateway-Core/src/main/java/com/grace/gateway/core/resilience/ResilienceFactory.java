package com.grace.gateway.core.resilience;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.grace.gateway.common.enums.CircuitBreakerEnum;
import com.grace.gateway.config.manager.DynamicConfigManager;
import com.grace.gateway.config.pojo.RouteDefinition;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹性策略工厂类
 * 负责创建和管理Resilience4j框架中的各种弹性策略实例（重试、熔断、隔离等）
 * 基于服务名称隔离不同服务的策略配置，支持动态配置更新
 */
public class ResilienceFactory {

    // 存储重试策略实例的缓存，key为服务名称
    private static final Map<String, Retry> retryMap = new ConcurrentHashMap<>();
    // 存储熔断策略实例的缓存，key为服务名称
    private static final Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    // 存储信号量隔离策略实例的缓存，key为服务名称
    private static final Map<String, Bulkhead> bulkheadMap = new ConcurrentHashMap<>();
    // 存储线程池隔离策略实例的缓存，key为服务名称
    private static final Map<String, ThreadPoolBulkhead> threadPoolBulkheadMap = new ConcurrentHashMap<>();

    // 记录已添加路由监听器的重试策略对应的服务名称，避免重复添加监听器
    private static final Set<String> retrySet = new ConcurrentHashSet<>();
    // 记录已添加路由监听器的熔断策略对应的服务名称
    private static final Set<String> circuitBreakerSet = new ConcurrentHashSet<>();
    // 记录已添加路由监听器的信号量隔离策略对应的服务名称
    private static final Set<String> bulkheadSet = new ConcurrentHashSet<>();
    // 记录已添加路由监听器的线程池隔离策略对应的服务名称
    private static final Set<String> threadPoolBulkheadSet = new ConcurrentHashSet<>();

    /**
     * 构建重试策略实例
     * @param resilienceConfig 弹性策略配置
     * @param serviceName 服务名称（用于隔离不同服务的策略）
     * @return 重试策略实例，若未启用则返回null
     */
    public static Retry buildRetry(RouteDefinition.ResilienceConfig resilienceConfig, String serviceName) {
        // 如果未启用重试策略，直接返回null
        if (!resilienceConfig.isRetryEnabled()) {
            return null;
        }
        // 使用computeIfAbsent实现：若缓存中存在则直接返回，否则创建新实例并缓存
        return retryMap.computeIfAbsent(serviceName, name -> {
            // 为服务添加路由监听器（仅首次创建时添加）
            if (!retrySet.contains(serviceName)) {
                // 当路由配置更新时，移除旧的重试策略实例（下次会自动创建新实例）
                DynamicConfigManager.getInstance().addRouteListener(serviceName, newRoute -> retryMap.remove(newRoute.getServiceName()));
                retrySet.add(serviceName);
            }
            // 构建重试策略配置
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(resilienceConfig.getMaxAttempts()) // 最大重试次数
                    .waitDuration(Duration.ofMillis(resilienceConfig.getWaitDuration())) // 重试间隔时间
                    .build();
            // 创建并返回重试策略实例
            return RetryRegistry.of(config).retry(serviceName);
        });
    }

    /**
     * 构建熔断策略实例
     * @param resilienceConfig 弹性策略配置
     * @param serviceName 服务名称
     * @return 熔断策略实例，若未启用则返回null
     */
    public static CircuitBreaker buildCircuitBreaker(RouteDefinition.ResilienceConfig resilienceConfig, String serviceName) {
        // 如果未启用熔断策略，直接返回null
        if (!resilienceConfig.isCircuitBreakerEnabled()) {
            return null;
        }
        // 从缓存获取或创建熔断实例
        return circuitBreakerMap.computeIfAbsent(serviceName, name -> {
            // 添加路由监听器，支持配置动态更新
            if (!circuitBreakerSet.contains(serviceName)) {
                DynamicConfigManager.getInstance().addRouteListener(serviceName, newRoute -> circuitBreakerMap.remove(newRoute.getServiceName()));
                circuitBreakerSet.add(serviceName);
            }
            // 构建熔断策略配置
            CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(resilienceConfig.getFailureRateThreshold()) // 失败率阈值（触发熔断）
                    .slowCallRateThreshold(resilienceConfig.getSlowCallRateThreshold()) // 慢调用率阈值
                    .waitDurationInOpenState(Duration.ofMillis(resilienceConfig.getWaitDurationInOpenState())) // 开放状态持续时间
                    .slowCallDurationThreshold(Duration.ofSeconds(resilienceConfig.getSlowCallDurationThreshold())) // 慢调用判定阈值
                    .permittedNumberOfCallsInHalfOpenState(resilienceConfig.getPermittedNumberOfCallsInHalfOpenState()) // 半开状态允许的调用数
                    .minimumNumberOfCalls(resilienceConfig.getMinimumNumberOfCalls()) // 计算失败率的最小调用数
                    .slidingWindowType(slidingWindowTypeConvert(resilienceConfig.getType())) // 滑动窗口类型（计数/时间）
                    .slidingWindowSize(resilienceConfig.getSlidingWindowSize()) // 滑动窗口大小
                    .build();
            // 创建并返回熔断策略实例
            return CircuitBreakerRegistry.of(circuitBreakerConfig).circuitBreaker(serviceName);
        });
    }

    /**
     * 构建信号量隔离策略实例
     * @param resilienceConfig 弹性策略配置
     * @param serviceName 服务名称
     * @return 信号量隔离实例，若未启用则返回null
     */
    public static Bulkhead buildBulkHead(RouteDefinition.ResilienceConfig resilienceConfig, String serviceName) {
        if (!resilienceConfig.isBulkheadEnabled()) {
            return null;
        }
        return bulkheadMap.computeIfAbsent(serviceName, name -> {
            if (!bulkheadSet.contains(serviceName)) {
                DynamicConfigManager.getInstance().addRouteListener(serviceName, newRoute -> bulkheadMap.remove(newRoute.getServiceName()));
                bulkheadSet.add(serviceName);
            }
            // 构建信号量隔离配置
            BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                    .maxConcurrentCalls(resilienceConfig.getMaxConcurrentCalls()) // 最大并发调用数
                    .maxWaitDuration(Duration.ofMillis(resilienceConfig.getMaxWaitDuration())) // 等待信号量的最大时间
                    .fairCallHandlingStrategyEnabled(resilienceConfig.isFairCallHandlingEnabled()) // 是否启用公平策略
                    .build();
            return BulkheadRegistry.of(bulkheadConfig).bulkhead(serviceName);
        });
    }

    /**
     * 构建线程池隔离策略实例
     * @param resilienceConfig 弹性策略配置
     * @param serviceName 服务名称
     * @return 线程池隔离实例，若未启用则返回null
     */
    public static ThreadPoolBulkhead buildThreadPoolBulkhead(RouteDefinition.ResilienceConfig resilienceConfig, String serviceName) {
        if (!resilienceConfig.isThreadPoolBulkheadEnabled()) {
            return null;
        }
        return threadPoolBulkheadMap.computeIfAbsent(serviceName, name -> {
            if (!threadPoolBulkheadSet.contains(serviceName)) {
                DynamicConfigManager.getInstance().addRouteListener(serviceName, newRoute -> threadPoolBulkheadMap.remove(newRoute.getServiceName()));
                threadPoolBulkheadSet.add(serviceName);
            }
            // 构建线程池隔离配置
            ThreadPoolBulkheadConfig threadPoolBulkheadConfig = ThreadPoolBulkheadConfig.custom()
                    .coreThreadPoolSize(resilienceConfig.getCoreThreadPoolSize()) // 核心线程数
                    .maxThreadPoolSize(resilienceConfig.getMaxThreadPoolSize()) // 最大线程数
                    .queueCapacity(resilienceConfig.getQueueCapacity()) // 队列容量
                    .build();
            return ThreadPoolBulkheadRegistry.of(threadPoolBulkheadConfig).bulkhead(serviceName);
        });
    }

    /**
     * 将自定义的熔断窗口类型枚举转换为Resilience4j框架的枚举类型
     * @param from 自定义枚举（CircuitBreakerEnum）
     * @return Resilience4j的滑动窗口类型枚举
     */
    private static CircuitBreakerConfig.SlidingWindowType slidingWindowTypeConvert(CircuitBreakerEnum from) {
        if (from == CircuitBreakerEnum.TIME_BASED) {
            return CircuitBreakerConfig.SlidingWindowType.TIME_BASED; // 时间-based滑动窗口
        } else {
            return CircuitBreakerConfig.SlidingWindowType.COUNT_BASED; // 计数-based滑动窗口
        }
    }

}


/*设计职责
        作为工厂类，负责创建 Resilience4j 框架中的各种弹性策略实例（重试、熔断、两种隔离策略）
        基于服务名称实现策略隔离（不同服务的策略独立存储和管理）
        提供缓存机制避免重复创建实例，提升性能
        支持动态配置更新（当路由配置变化时，自动失效旧策略实例）
        核心数据结构
        四个Map（如retryMap、circuitBreakerMap）：缓存不同服务的弹性策略实例，key 为服务名称
        四个Set（如retrySet、circuitBreakerSet）：记录已添加配置监听器的服务，避免重复添加
        关键实现细节
        延迟初始化：通过ConcurrentHashMap.computeIfAbsent实现 "按需创建"，只有当策略被实际使用时才初始化
        动态配置支持：
        为每个服务首次创建策略时，通过DynamicConfigManager注册路由监听器
        当路由配置更新时，监听器会移除缓存中的旧策略实例，下次使用时会自动创建新实例（基于新配置）
        配置映射：将网关自定义的配置类（ResilienceConfig）转换为 Resilience4j 框架所需的配置对象（如RetryConfig、CircuitBreakerConfig）
        各策略构建逻辑
        所有策略的构建流程一致：检查是否启用 → 检查缓存 → 注册监听器 → 构建配置 → 创建实例并缓存
        每个策略的配置项都与ResilienceConfig中的属性一一对应，实现了自定义配置到框架配置的映射
        线程安全保障
        使用ConcurrentHashMap和ConcurrentHashSet确保多线程环境下的操作安全
        监听器的添加通过Set判断实现了幂等性，避免重复操作*/










