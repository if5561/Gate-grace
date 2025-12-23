package com.grace.gateway.core.filter.route;

import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.helper.ResponseHelper;
import com.grace.gateway.core.http.HttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 路由工具类
 * 提供构建路由请求供应器的功能，封装了请求发送和响应处理的逻辑
 */
public class RouteUtil {
    /**
     * 构建路由请求的供应器(Supplier)
     * 供应器负责创建HTTP请求、发送请求并处理响应结果
     *
     * @param context 网关上下文对象，包含请求信息和处理状态
     * @return 返回一个供应器，其提供的CompletionStage会在请求完成后触发
     */
    public static Supplier<CompletionStage<Response>> buildRouteSupplier(GatewayContext context) {
        // 返回一个Supplier函数式接口的实现
        return () -> {
            // 1. 从上下文获取请求对象并构建异步HTTP客户端需要的Request对象
            Request request = context.getRequest().build();
            // 2. 使用HttpClient单例发送请求，获取异步结果CompletableFuture
            CompletableFuture<Response> future = HttpClient.getInstance().executeRequest(request);
            // 3. 注册请求完成后的回调函数
            future.whenComplete(((response, throwable) -> {
                // 3.1 如果发生异常
                if (throwable != null) {
                    // 将异常存储到上下文
                    context.setThrowable(throwable);
                    // 抛出运行时异常，会被上层的exceptionally()捕获
                    throw new RuntimeException(throwable);
                }
                // 3.2 如果请求成功，处理响应
                // 将HTTP响应转换为网关统一响应格式并存储到上下文
                context.setResponse(ResponseHelper.buildGatewayResponse(response));
                // 继续执行过滤器链的下一个过滤器
                context.doFilter();
            }));
            // 返回异步结果对象
            return future;
        };
    }

}
