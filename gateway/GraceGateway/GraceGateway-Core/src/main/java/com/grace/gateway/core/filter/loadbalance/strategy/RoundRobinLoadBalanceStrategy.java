package com.grace.gateway.core.filter.loadbalance.strategy;

import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.config.util.FilterUtil;
import com.grace.gateway.core.context.GatewayContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.grace.gateway.common.constant.FilterConstant.LOAD_BALANCE_FILTER_NAME;
import static com.grace.gateway.common.constant.LoadBalanceConstant.ROUND_ROBIN_LOAD_BALANCE_STRATEGY;

/**
 * 轮询负载均衡策略
 * 按照顺序依次将请求分配到服务实例，支持普通轮询和严格轮询（线程安全）两种模式
 * 适用于实例性能相近的无状态服务，能保证请求在实例间均匀分配
 */
public class RoundRobinLoadBalanceStrategy implements LoadBalanceStrategy {
    /**
     * 严格轮询的位置计数器映射表（线程安全）
     * key: 服务名称（区分不同服务的轮询状态）
     * value: AtomicInteger（原子整数，记录当前轮询位置，支持线程安全的自增操作）
     */
    Map<String, AtomicInteger> strictPositionMap = new ConcurrentHashMap<>();
    /**
     * 普通轮询的位置计数器映射表（非线程安全，性能更高）
     * key: 服务名称
     * value: Integer（记录当前轮询位置）
     */
    Map<String, Integer> positionMap = new ConcurrentHashMap<>();
    /**
     * 轮询位置重置的安全阈值
     * 避免计数器值过大导致的整数溢出，取值为Integer.MAX_VALUE的1/4
     */
    private final int THRESHOLD = Integer.MAX_VALUE >> 2;
    /**
     * 从实例列表中按轮询方式选择目标实例
     * 核心逻辑：根据配置选择普通轮询或严格轮询，按顺序循环分配请求
     *
     * @param context  网关上下文对象，包含请求信息和路由配置
     * @param instances 可用的服务实例列表（已过滤出健康实例）
     * @return 按轮询顺序选中的服务实例
     */
    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        // 默认启用严格轮询（线程安全模式）
        boolean isStrictRoundRobin = true;
        // 从路由配置中获取负载均衡过滤器的详细配置
        RouteDefinition.LoadBalanceFilterConfig loadBalanceFilterConfig = FilterUtil.findFilterConfigByClass(
                context.getRoute().getFilterConfigs(), LOAD_BALANCE_FILTER_NAME, RouteDefinition.LoadBalanceFilterConfig.class);

        // 若配置存在，根据配置决定是否启用严格轮询
        if (loadBalanceFilterConfig != null) {
            isStrictRoundRobin = loadBalanceFilterConfig.isStrictRoundRobin();
        }
        // 获取当前服务名称（用于区分不同服务的轮询计数器）
        String serviceName = context.getRequest().getServiceDefinition().getServiceName();
        ServiceInstance serviceInstance;
        if (isStrictRoundRobin) {
            // 严格轮询模式（线程安全）
            // 若服务名称不存在于映射表，初始化计数器为0；否则直接获取
            AtomicInteger strictPosition = strictPositionMap.computeIfAbsent(serviceName, k -> new AtomicInteger(0));
            // 原子方式获取当前位置并自增（线程安全）
            int index = Math.abs(strictPosition.getAndIncrement());
            // 按实例数量取模，得到当前应选择的实例索引
            serviceInstance = instances.get(index % instances.size());
            // 当计数器达到阈值时，重置为下一个位置（避免整数溢出）
            if (index >= THRESHOLD) {
                strictPosition.set((index + 1) % instances.size());
            }
        } else {
            // 普通轮询模式（非线程安全，性能更高）
            // 获取当前服务的轮询位置（默认0）
            int position = positionMap.getOrDefault(serviceName, 0);
            // 计算当前索引并自增位置
            int index = Math.abs(position++);
            // 按实例数量取模，选择实例
            serviceInstance = instances.get(index % instances.size());
            // 当位置达到阈值时，重置为下一个位置（避免整数溢出）
            if (position >= THRESHOLD) {
                positionMap.put(serviceName, (position + 1) % instances.size());
            } else {
                // 未达阈值时，直接更新位置
                positionMap.put(serviceName, position);
            }
        }
        return serviceInstance;
    }
    /**
     * 获取当前负载均衡策略的标识
     * 用于策略管理器注册和选择策略时识别
     *
     * @return 策略标识，固定为ROUND_ROBIN_LOAD_BALANCE_STRATEGY
     */
    @Override
    public String mark() {
        return ROUND_ROBIN_LOAD_BALANCE_STRATEGY;
    }
}
