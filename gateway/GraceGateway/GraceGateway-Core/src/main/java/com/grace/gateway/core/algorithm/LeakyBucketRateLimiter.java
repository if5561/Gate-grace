package com.grace.gateway.core.algorithm;

import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.common.exception.LimitedException;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.flow.RateLimiter;
import io.netty.channel.EventLoopGroup;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 漏桶算法实现类，用于网关的流量控制
 * 漏桶算法核心思想：水（请求）先进入桶，桶以固定速率出水（处理请求），桶满则拒绝水进入
 * 适用于平滑突发流量，保证下游服务按稳定速率处理请求
 */
public class LeakyBucketRateLimiter implements RateLimiter {

    // 漏桶的容量，即桶最多能容纳的请求数
    private final int bucketCapacity;
    // 漏水的时间间隔（单位：毫秒），决定桶处理请求的速率
    private final long leakInterval;
    // 当前桶中的请求数量（水量）， AtomicInteger 保证线程安全
    private final AtomicInteger currentWaterLevel;
    // 等待队列，用于暂存无法立即处理的请求（桶未满时先入队，等待漏水后处理）
    private final Queue<GatewayContext> waitingQueue;

    /**
     * 构造漏桶限流器
     * @param capacity 桶的容量
     * @param leakInterval 漏水时间间隔（毫秒）
     * @param eventLoopGroup Netty 的事件循环组，用于定时执行漏水任务
     */
    public LeakyBucketRateLimiter(int capacity, long leakInterval, EventLoopGroup eventLoopGroup) {
        this.bucketCapacity = capacity;
        this.leakInterval = leakInterval;
        this.currentWaterLevel = new AtomicInteger(0);
        this.waitingQueue = new ConcurrentLinkedQueue<>();
        // 启动定时漏水任务，按固定间隔处理队列中的请求
        startLeakTask(eventLoopGroup);
    }

    /**
     * 启动定时漏水任务
     * 作用：以固定速率从桶中取出请求，提交给 Netty 事件循环处理
     * @param eventLoopGroup Netty 事件循环组，提供定时任务和请求处理的线程池
     */
    private void startLeakTask(EventLoopGroup eventLoopGroup) {
        // 使用 Netty 的定时任务，按 leakInterval 间隔执行
        eventLoopGroup.scheduleAtFixedRate(() -> {
            // 队列非空且桶中有请求时，处理一个请求
            if (!waitingQueue.isEmpty() && currentWaterLevel.get() > 0) {
                // 从队列取出最早进入的请求上下文
                GatewayContext gatewayContext = waitingQueue.poll();
                if (gatewayContext != null) {
                    // 将请求重新提交到 Netty 事件循环执行（保证线程模型正确）
                    gatewayContext.getNettyCtx().executor().execute(() -> {
                        // 处理请求前，桶中请求数减 1（漏水）
                        currentWaterLevel.decrementAndGet();
                        // 继续执行网关的过滤链（处理请求）
                        gatewayContext.doFilter();
                    });
                }
            }
        }, leakInterval, leakInterval, TimeUnit.MILLISECONDS); // 初次延迟、间隔、时间单位
    }

    /**
     * 尝试消耗桶的容量（处理请求）
     * 实现 RateLimiter 接口，网关在处理请求前会调用此方法
     * @param context 网关请求上下文，包含请求处理所需的信息
     * @throws LimitedException 桶满时抛出异常，拒绝请求
     */
    @Override
    public void tryConsume(GatewayContext context) {
        if (currentWaterLevel.get() < bucketCapacity) {
            // 桶未满：请求入队，桶中请求数加 1
            currentWaterLevel.incrementAndGet();
            waitingQueue.offer(context);
        } else {
            // 桶已满：直接拒绝请求，抛出限流异常
            throw new LimitedException(ResponseCode.TOO_MANY_REQUESTS);
        }
    }
}
