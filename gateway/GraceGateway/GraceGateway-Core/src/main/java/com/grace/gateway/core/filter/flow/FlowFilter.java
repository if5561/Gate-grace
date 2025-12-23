package com.grace.gateway.core.filter.flow;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.grace.gateway.config.manager.DynamicConfigManager;
import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.util.FilterUtil;
import com.grace.gateway.core.algorithm.LeakyBucketRateLimiter;
import com.grace.gateway.core.algorithm.SlidingWindowRateLimiter;
import com.grace.gateway.core.algorithm.TokenBucketRateLimiter;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.Filter;
import io.netty.channel.EventLoop;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.grace.gateway.common.constant.FilterConstant.FLOW_FILTER_NAME;
import static com.grace.gateway.common.constant.FilterConstant.FLOW_FILTER_ORDER;

/**
 * 流量控制过滤器（限流过滤器）
 * 基于不同的限流算法（令牌桶、滑动窗口、漏桶）实现请求限流，
 * 保证后端服务不被过量请求压垮，保护服务稳定性。
 * 核心逻辑：
 * 1. 从路由配置中读取限流规则
 * 2. 为每个服务动态创建/复用限流算法实例
 * 3. 拦截请求时执行限流判断，决定是否放行或拒绝
 */
public class FlowFilter implements Filter {
    // 存储“服务名 -> 限流算法实例”的映射，保证线程安全（ConcurrentHashMap）
    // 每个服务独立维护限流逻辑，避免不同服务流量互相影响
    private final ConcurrentHashMap<String /* 服务名 */, RateLimiter> rateLimiterMap = new ConcurrentHashMap<>();

    // 记录已添加路由监听器的服务名，避免重复添加监听器
    private final Set<String> addListener = new ConcurrentHashSet<>();

    @Override
    public void doPreFilter(GatewayContext context) {
        // 从路由配置中查找流量控制过滤器的配置（FlowFilterConfig）
        // FilterUtil.findFilterConfigByClass：根据过滤器名称和类型，从路由配置中提取配置
        RouteDefinition.FlowFilterConfig flowFilterConfig = Optional
                .ofNullable(FilterUtil.findFilterConfigByClass(
                        context.getRoute().getFilterConfigs(),
                        FLOW_FILTER_NAME,
                        RouteDefinition.FlowFilterConfig.class
                ))
                .orElse(new RouteDefinition.FlowFilterConfig()); // 无配置时用默认配置
        // 如果限流功能未启用，直接执行后续过滤器逻辑
        if (!flowFilterConfig.isEnabled()) {
            context.doFilter();
            return;
        }
        // 获取当前请求的服务名（服务维度做限流）
        String serviceName = context.getRequest().getServiceDefinition().getServiceName();
        // 为服务创建/复用限流算法实例（computeIfAbsent：不存在则创建，存在则直接获取）
        RateLimiter rateLimiter = rateLimiterMap.computeIfAbsent(serviceName, name -> {
            // 为服务添加路由配置变更监听器，配置变更时删除旧的限流实例（下次请求会重建）
            if (!addListener.contains(name)) {
                DynamicConfigManager.getInstance().addRouteListener(name, newRoute -> {
                    rateLimiterMap.remove(newRoute.getServiceName()); // 删除旧限流实例
                });
                addListener.add(name); // 标记已添加监听器
            }
            // 根据配置初始化限流算法（令牌桶、滑动窗口、漏桶）
            return initRateLimiter(flowFilterConfig, context.getNettyCtx().channel().eventLoop());
        });
        // 执行限流判断：尝试获取令牌/检查流量是否超限
        rateLimiter.tryConsume(context);
    }

    @Override
    public void doPostFilter(GatewayContext context) {
        context.doFilter();
    }

    @Override
    public String mark() {
        // 返回过滤器名称，用于在过滤器链中标识和查找
        return FLOW_FILTER_NAME;
    }

    @Override
    public int getOrder() {
        // 返回过滤器执行优先级，决定在过滤器链中的执行顺序
        return FLOW_FILTER_ORDER;
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")//抑制警告注解
    private RateLimiter initRateLimiter(
            RouteDefinition.FlowFilterConfig flowFilterConfig,
            EventLoop eventLoop
    ) {
        // 根据配置的限流算法类型，初始化对应的限流实例
        switch (flowFilterConfig.getType()) {
            case TOKEN_BUCKET:
                // 令牌桶算法：按固定速率生成令牌，请求需获取令牌才能通过
                return new TokenBucketRateLimiter(
                        flowFilterConfig.getCapacity(), // 令牌桶容量
                        flowFilterConfig.getRate()      // 令牌生成速率（单位时间生成多少令牌）
                );
            case SLIDING_WINDOW:
                // 滑动窗口算法：统计单位时间内的请求数，超过阈值则限流
                return new SlidingWindowRateLimiter(
                        flowFilterConfig.getCapacity(), // 窗口内允许的最大请求数
                        flowFilterConfig.getRate()      // 窗口时间（如 1 秒）
                );
            case LEAKY_BUCKET:
                // 漏桶算法：请求先进入桶，按固定速率流出，桶满则拒绝
                return new LeakyBucketRateLimiter(
                        flowFilterConfig.getCapacity(), // 漏桶容量
                        flowFilterConfig.getRate(),     // 漏桶流出速率
                        eventLoop                       // Netty 的 EventLoop，用于定时任务
                );
            default:
                // 默认使用令牌桶算法
                return new TokenBucketRateLimiter(
                        flowFilterConfig.getCapacity(),
                        flowFilterConfig.getRate()
                );
        }
    }
}
