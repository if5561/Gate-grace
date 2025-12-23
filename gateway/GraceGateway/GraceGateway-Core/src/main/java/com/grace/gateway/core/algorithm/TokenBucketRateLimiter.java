package com.grace.gateway.core.algorithm;

import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.common.exception.LimitedException;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.flow.RateLimiter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 令牌桶算法实现类，用于网关的流量控制
 * 核心思想：桶以固定速率生成令牌，请求需要获取令牌才能通过；桶满时不再生成新令牌
 * 适用于允许突发流量（桶积累令牌后可一次性消耗）的场景
 */
public class TokenBucketRateLimiter implements RateLimiter {

    // 桶的最大容量（最多可积累的令牌数）
    private final int capacity;
    // 令牌生成速率（每秒生成的令牌数）
    private final int refillRate;
    // 当前桶中的令牌数量，AtomicInteger 保证线程安全
    private final AtomicInteger tokens;

    /**
     * 构造令牌桶限流器
     * @param capacity 桶的容量（最大令牌数）
     * @param refillRatePerSecond 令牌生成速率（每秒生成的数量）
     */
    public TokenBucketRateLimiter(int capacity, int refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRate = refillRatePerSecond;
        this.tokens = new AtomicInteger(0);
        // 启动令牌补充任务
        startRefilling();
    }

    /**
     * 尝试消耗令牌处理请求
     * 核心逻辑：获取令牌（令牌数减1），判断是否成功获取有效令牌
     * @param context 网关请求上下文，包含请求处理所需的信息
     * @throws LimitedException 当没有可用令牌时抛出限流异常
     */
    @Override
    public void tryConsume(GatewayContext context) {
        // 先获取令牌（原子操作：令牌数减1）
        if (tokens.getAndDecrement() > 0) {
            // 令牌数>0，说明获取到有效令牌，继续处理请求
            context.doFilter();
        } else {
            // 令牌数<=0，说明没有可用令牌，需将令牌数加回（补偿操作）
            tokens.incrementAndGet();
            // 抛出限流异常，拒绝请求
            throw new LimitedException(ResponseCode.TOO_MANY_REQUESTS);
        }
    }

    /**
     * 启动令牌补充任务
     * 作用：以固定速率（每秒）向桶中补充令牌
     */
    private void startRefilling() {
        // 创建单线程的定时任务池
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        // 定时任务逻辑：
        // - 初始延迟 0ms（立即开始）
        // - 每隔 1000ms（1秒）执行一次 refillTokens 方法
        scheduler.scheduleAtFixedRate(this::refillTokens, 0, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * 补充令牌的具体逻辑
     * 每次补充时，保证令牌数不超过桶的容量（Math.min 实现）
     */
    private void refillTokens() {
        // 新令牌数 = 原令牌数 + 生成速率
        // 但不能超过桶的最大容量，避免令牌溢出
        tokens.set(Math.min(capacity, tokens.get() + refillRate));
    }
}
