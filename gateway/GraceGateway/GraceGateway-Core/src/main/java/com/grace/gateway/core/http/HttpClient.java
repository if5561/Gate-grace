package com.grace.gateway.core.http;


import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;

import java.util.concurrent.CompletableFuture;
/**
 * 网关 HTTP 客户端封装类
 * 基于 AsyncHttpClient 实现异步 HTTP 请求的发送，
 * 为网关转发请求到后端服务提供统一的 HTTP 调用能力，
 * 内部采用单例模式保证全局唯一实例，方便统一管理和初始化
 */
public class HttpClient {

    // AsyncHttpClient 核心实例，用于实际发送异步 HTTP 请求
    // AsyncHttpClient 是异步 HTTP 客户端框架的核心对象，负责管理连接、执行请求等   实际执行 HTTP 请求的核心对象
    private AsyncHttpClient asyncHttpClient;

    // 私有构造方法，防止外部直接实例，防止外部直接实例化，确保单例模式
    private HttpClient() {
    }

    // 静态单例实例，类加载时创建，保证全局唯一   网关 HTTP 客户端的统一入口和管理者，封装了与后端服务通信的逻辑
    private final static HttpClient INSTANCE = new HttpClient();

    /**
     * 获取 HttpClient 单例实例
     * @return 全局唯一的 HttpClient 实例
     */
    public static HttpClient getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化 AsyncHttpClient 实例
     * 需在系统启动阶段调用，为 HttpClient 注入实际发送请求的核心对象
     * @param asyncHttpClient 外部创建好的 AsyncHttpClient 实例
     */
    public void initialized(AsyncHttpClient asyncHttpClient) {
        this.asyncHttpClient = asyncHttpClient;
    }
    /**
     * 执行 HTTP 请求，返回 CompletableFuture 以便异步处理响应
     * @param request 构建好的 AsyncHttpClient 请求对象（包含 URL、方法、头信息等）
     * @return CompletableFuture<Response> 异步结果，可通过 thenApply、whenComplete 等方法处理响应或异常
     */
    public CompletableFuture<Response> executeRequest(Request request) {
        // 调用 AsyncHttpClient 执行请求，返回其原生的 ListenableFuture
        ListenableFuture<Response> future = asyncHttpClient.executeRequest(request);
        // 将 ListenableFuture 转换为 Java 标准的 CompletableFuture，方便与其他异步逻辑整合
        return future.toCompletableFuture();
    }
}
