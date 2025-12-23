package com.grace.gateway.core.filter.flow;

import com.grace.gateway.core.context.GatewayContext;
//定义限流算法的通用接口，所有具体限流算法（如令牌桶、滑动窗口）都需实现此接口
public interface RateLimiter {
//    tryConsume 用于判断当前请求是否允许通过（若超过限流阈值，则在此方法中拦截请求并返回错误响应）。
    void tryConsume(GatewayContext context);
}
