package com.grace.gateway.core.netty;

import com.grace.gateway.common.util.SystemUtil;
import com.grace.gateway.config.config.Config;
import com.grace.gateway.config.config.HttpClientConfig;
import com.grace.gateway.core.config.LifeCycle;
import com.grace.gateway.core.http.HttpClient;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于Netty的异步HTTP客户端实现类
 * 负责网关内部发起HTTP请求的客户端组件，实现生命周期管理接口
 * 封装了异步HTTP客户端的创建、配置和资源释放逻辑
 */
@Slf4j
public class NettyHttpClient implements LifeCycle {
    /** 网关全局配置对象，包含HTTP客户端相关配置 */
    private final Config config;
    /**
     * Netty事件循环组（工作线程组）
     * 负责处理HTTP客户端的I/O操作，根据系统类型选择Epoll或NIO实现
     */
    private final EventLoopGroup eventLoopGroupWorker;
    /**
     * 客户端启动状态标识
     * 使用原子布尔值确保线程安全的启动/关闭控制
     */
    private final AtomicBoolean start = new AtomicBoolean(false);
    /** 异步HTTP客户端实例，基于AsyncHttpClient实现 */
    private AsyncHttpClient asyncHttpClient;
    /**
     * 构造函数，初始化Netty事件循环组
     *
     * @param config 网关全局配置对象
     */
    public NettyHttpClient(Config config) {
        this.config = config;

        // 根据系统是否支持Epoll选择合适的事件循环组实现
        if (SystemUtil.useEpoll()) {
            // Linux系统使用Epoll模型，性能更优
            this.eventLoopGroupWorker = new EpollEventLoopGroup(
                    config.getHttpClient().getEventLoopGroupWorkerNum(),
                    new DefaultThreadFactory("epoll-http-client-worker-nio")
            );
        } else {
            // 其他系统使用NIO模型，保证跨平台兼容性
            this.eventLoopGroupWorker = new NioEventLoopGroup(
                    config.getHttpClient().getEventLoopGroupWorkerNum(),
                    new DefaultThreadFactory("default-http-client-worker-nio")
            );
        }
    }

    /**
     * 启动HTTP客户端，初始化异步HTTP客户端实例
     * 配置连接参数并设置全局HTTP客户端实例
     */
    @Override
    public void start() {
        // 使用CAS操作确保客户端只被启动一次
        if (!start.compareAndSet(false, true)) {
            log.warn("NettyHttpClient has already started");
            return;
        }

        // 获取HTTP客户端配置
        HttpClientConfig httpClientConfig = config.getHttpClient();

        // 构建异步HTTP客户端配置
        DefaultAsyncHttpClientConfig.Builder builder = new DefaultAsyncHttpClientConfig.Builder()
                .setEventLoopGroup(eventLoopGroupWorker) // 关联Netty事件循环组
                .setConnectTimeout(httpClientConfig.getHttpConnectTimeout()) // 连接超时时间
                .setRequestTimeout(httpClientConfig.getHttpRequestTimeout()) // 请求超时时间
                .setMaxRedirects(httpClientConfig.getHttpMaxRedirects()) // 最大重定向次数
                .setAllocator(PooledByteBufAllocator.DEFAULT) // 使用池化ByteBuf分配器，提升性能和内存利用率
                .setCompressionEnforced(true) // 强制启用压缩，减少网络传输数据量
                .setMaxConnections(httpClientConfig.getHttpMaxConnections()) // 全局最大连接数
                .setMaxConnectionsPerHost(httpClientConfig.getHttpConnectionsPerHost()) // 每个主机的最大连接数
                .setPooledConnectionIdleTimeout(httpClientConfig.getHttpPooledConnectionIdleTimeout()); // 连接池空闲连接超时时间

        // 创建异步HTTP客户端实例
        this.asyncHttpClient = new DefaultAsyncHttpClient(builder.build());

        // 初始化全局HTTP客户端单例
        HttpClient.getInstance().initialized(asyncHttpClient);

        log.info("NettyHttpClient started successfully");
    }

    /**
     * 关闭HTTP客户端，释放相关资源
     * 优雅关闭异步HTTP客户端，避免资源泄露
     */
    @Override
    public void shutdown() {
        if (!start.get()) {
            log.warn("NettyHttpClient is not running");
            return;
        }

        // 关闭异步HTTP客户端
        if (asyncHttpClient != null) {
            try {
                this.asyncHttpClient.close();
                log.info("NettyHttpClient closed successfully");
            } catch (IOException e) {
                log.error("Failed to close NettyHttpClient", e);
            }
        }

        // 优雅关闭事件循环组
        if (eventLoopGroupWorker != null) {
            eventLoopGroupWorker.shutdownGracefully();
        }
    }
    /**
     * 判断客户端是否已启动
     *
     * @return 若已启动则返回true，否则返回false
     */
    @Override
    public boolean isStarted() {
        return start.get();
    }
}
