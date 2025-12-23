package com.grace.gateway.core.algorithm;

import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.common.exception.LimitedException;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.flow.RateLimiter;

import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;

/**
 * 基于 “请求时间戳队列” 的固定窗口滑动限流算法实现类
 * 核心思想：在一个时间窗口内控制请求数量，窗口随时间滑动，保证流量的平滑控制
 * 适用于需要限制单位时间内请求频率的场景（如接口限流）
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    // 窗口内最大允许的请求数量（容量）
    private final int capacity;
    // 时间窗口大小（毫秒），如 1000 表示 1 秒的窗口
    private final int windowSizeInMillis;
    // 存储请求时间戳的双端队列，用于维护滑动窗口内的请求记录
    // LinkedList 实现 Deque 接口，支持高效的首尾操作
    private final Deque<Long> requestTimestamps;

    /**
     * 构造滑动窗口限流器
     * @param capacity 窗口内最大允许的请求数
     * @param windowSizeInMillis 时间窗口大小（毫秒）
     */
    public SlidingWindowRateLimiter(int capacity, int windowSizeInMillis) {
        this.capacity = capacity;
        this.windowSizeInMillis = windowSizeInMillis;
        // 初始化双端队列，用于存储请求时间戳
        this.requestTimestamps = new LinkedList<>();
    }

    /**
     * 尝试处理请求，执行限流逻辑
     * 注意：方法用 synchronized 保证线程安全，多线程下确保窗口内请求计数准确
     * @param context 网关请求上下文，包含请求处理所需的信息
     * @throws LimitedException 当超过限流阈值时抛出异常
     */
    @Override
    public synchronized void tryConsume(GatewayContext context) {
        // 获取当前时间戳（毫秒），作为滑动窗口的时间基准
        long now = Instant.now().toEpochMilli();
        // 清理窗口外的旧请求（保持队列中仅保留当前窗口内的请求）
        cleanOldRequests(now);

        // 判断当前窗口内的请求数量是否未超过容量
        if (requestTimestamps.size() < capacity) {
            // 将当前请求的时间戳加入队列
            requestTimestamps.addLast(now);
            // 继续执行网关的过滤链（请求通过限流，处理后续逻辑）
            context.doFilter();
        } else {
            // 窗口内请求数已达上限，抛出限流异常
            throw new LimitedException(ResponseCode.TOO_MANY_REQUESTS);
        }
    }

    /**
     * 清理时间窗口外的旧请求
     * 保证队列中仅保留当前时间窗口内的请求时间戳
     * @param currentTime 当前时间戳（毫秒）
     */
    private void cleanOldRequests(long currentTime) {
        // 循环检查队列头部（最旧的请求）是否在窗口外
        // 队列为空时终止循环，避免 peekFirst() 空指针
        while (!requestTimestamps.isEmpty()
                // 计算最旧请求与当前时间的差值
                && (currentTime - requestTimestamps.peekFirst()) > windowSizeInMillis) {
            // 移除窗口外的旧请求时间戳（从队列头部弹出）
            requestTimestamps.pollFirst();
        }
    }
}
