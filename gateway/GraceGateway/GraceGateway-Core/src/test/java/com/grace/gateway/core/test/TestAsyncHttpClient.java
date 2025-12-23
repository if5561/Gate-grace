package com.grace.gateway.core.test;

import com.grace.gateway.config.config.Config;
import com.grace.gateway.config.loader.ConfigLoader;
import com.grace.gateway.config.manager.DynamicConfigManager;
import com.grace.gateway.core.netty.NettyHttpServer;
import com.grace.gateway.core.netty.processor.NettyCoreProcessor;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.DefaultEventLoopGroup;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.*;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 测试类：用于 AsyncHttpClient 异步HTTP客户端和Netty服务器的交互
 * 主要用于验证网关系统中的异步HTTP请求处理能力
 */
@Slf4j  // Lombok注解，自动生成日志对象
public class TestAsyncHttpClient {

    // 网关配置对象，存储系统配置信息
    private Config config;

    // 异步HTTP客户端实例，用于发送非阻塞HTTP请求
    private AsyncHttpClient asyncHttpClient;

    // Netty HTTP服务器实例，模拟网关服务端
    private NettyHttpServer nettyHttpServer;

    /**
     * 测试前的初始化方法：
     * 1. 加载系统配置
     * 2. 初始化Netty服务器
     * 3. 配置并创建AsyncHttpClient实例
     */
    @Before
    public void before() {
        // 加载配置文件（null表示使用默认配置路径）
        config = ConfigLoader.load(null);

        // 初始化Netty服务器，传入配置和核心处理器
        nettyHttpServer = new NettyHttpServer(config, new NettyCoreProcessor());

        // 构建AsyncHttpClient的配置
        DefaultAsyncHttpClientConfig.Builder builder = new DefaultAsyncHttpClientConfig.Builder()
                .setEventLoopGroup(new DefaultEventLoopGroup()) // 设置Netty事件循环组，处理I/O事件
                .setConnectTimeout(300000) // 连接超时时间：300秒（高并发场景下避免频繁超时）
                .setRequestTimeout(3000)   // 请求超时时间：3秒（快速失败，避免资源阻塞）
                .setMaxRedirects(100)      // 最大重定向次数：100次
                .setAllocator(PooledByteBufAllocator.DEFAULT) // 使用池化ByteBuf分配器，提升内存使用效率
                .setCompressionEnforced(true) // 强制启用压缩，减少网络传输数据量
                .setMaxConnections(10000)    // 全局最大连接数：10000（支持高并发）
                .setMaxConnectionsPerHost(10000) // 每个主机最大连接数：10000
                .setPooledConnectionIdleTimeout(50000); // 连接池中空闲连接超时时间：50秒

        // 根据配置创建异步HTTP客户端实例
        asyncHttpClient = new DefaultAsyncHttpClient(builder.build());
    }

    /**
     * 测试异步HTTP请求发送
     * 验证AsyncHttpClient的非阻塞请求处理能力
     */
    @Test
    public void testHttp() throws ExecutionException, InterruptedException {
        // 创建HTTP请求构建器
        RequestBuilder builder = new RequestBuilder();
        // 目标测试URL（本地Netty服务器的ping接口）
        String url = "http://127.0.0.1:10001/http-server/ping1";
        builder.setMethod("GET"); // 设置请求方法为GET
        builder.setUrl(url);      // 设置请求URL

        // 发送异步HTTP请求，返回ListenableFuture对象（异步结果持有者）
        ListenableFuture<Response> future = asyncHttpClient.executeRequest(builder.build());

        // 将ListenableFuture转换为CompletableFuture，便于使用Java 8的函数式编程处理结果
        CompletableFuture<Response> responseCompletableFuture = future.toCompletableFuture();

        // 异步处理响应结果：当请求完成时触发（无论成功或失败）
        responseCompletableFuture.whenComplete((response, throwable) -> {
            // 打印响应结果和可能的异常
            log.info("响应结果: {}, \n异常信息: {}", response, throwable);
        });

        // 无限循环保持主线程存活，等待异步回调执行（测试场景专用）
        while (true) {}
    }

    /**
     * 测试Netty HTTP服务器启动
     * 用于启动网关服务器并加载路由配置
     */
    @Test
    public void testNettyServer() {
        // 启动Netty服务器，开始监听端口接收请求
        nettyHttpServer.start();
        // 更新动态路由配置（从加载的配置中获取路由信息）
        DynamicConfigManager.getInstance().updateRoutes(config.getRoutes());

        // 无限循环保持服务器运行（测试场景专用）
        while(true) {}
    }

}
