package com.grace.gateway.core.netty;

import com.grace.gateway.common.util.SystemUtil;
import com.grace.gateway.config.config.Config;
import com.grace.gateway.core.config.LifeCycle;
import com.grace.gateway.core.netty.handler.NettyHttpServerHandler;
import com.grace.gateway.core.netty.processor.NettyProcessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty HTTP服务器实现类
 * 负责启动和管理基于Netty的HTTP网关服务，实现生命周期管理接口
 *
 * @author 自动生成
 * @date 2025-08-08
 */
@Slf4j
public class NettyHttpServer implements LifeCycle {

    /** 网关全局配置对象 */
    private final Config config;

    /** Netty处理器，用于处理业务逻辑 */
    private final NettyProcessor nettyProcessor;

    /** 服务启动状态标识，确保线程安全的启停控制 */
    private final AtomicBoolean start = new AtomicBoolean(false);

    /** Netty服务器引导类，用于配置和启动服务器 */
    private ServerBootstrap serverBootstrap;

    /** 用于接收客户端连接的Boss线程组 */
    private EventLoopGroup eventLoopGroupBoss;

    /** 用于处理客户端IO操作的Worker线程组 */
    private EventLoopGroup eventLoopGroupWorker;

    /**
     * 构造函数，初始化Netty服务器
     *
     * @param config 网关配置对象
     * @param nettyProcessor 业务处理器
     */
    public NettyHttpServer(Config config, NettyProcessor nettyProcessor) {
        this.config = config;
        this.nettyProcessor = nettyProcessor;
        init();
    }

    /**
     * 初始化Netty服务器组件
     * 根据系统类型选择合适的IO模型（Epoll或NIO）
     */
    private void init() {
        this.serverBootstrap = new ServerBootstrap();

        // 根据系统是否支持Epoll选择不同的EventLoopGroup实现
        if (SystemUtil.useEpoll()) {
            // 使用Epoll模型（适用于Linux系统，性能更优）
            this.eventLoopGroupBoss = new EpollEventLoopGroup(
                    config.getNetty().getEventLoopGroupBossNum(),
                    new DefaultThreadFactory("epoll-netty-boss-nio")
            );
            this.eventLoopGroupWorker = new EpollEventLoopGroup(
                    config.getNetty().getEventLoopGroupWorkerNum(),
                    new DefaultThreadFactory("epoll-netty-worker-nio")
            );
        } else {
            // 使用NIO模型（跨平台默认选择）
            this.eventLoopGroupBoss = new NioEventLoopGroup(
                    config.getNetty().getEventLoopGroupBossNum(),
                    new DefaultThreadFactory("default-netty-boss-nio")
            );
            this.eventLoopGroupWorker = new NioEventLoopGroup(
                    config.getNetty().getEventLoopGroupWorkerNum(),
                    new DefaultThreadFactory("default-netty-worker-nio")
            );
        }
    }

    /**
     * 启动Netty服务器
     * 配置服务器参数并绑定到指定端口，同步等待启动完成
     */
    @SneakyThrows(InterruptedException.class)
    @Override
    public void start() {
        // 使用CAS操作确保服务只被启动一次
        if (!start.compareAndSet(false, true)) {
            log.warn("Netty server has already started");
            return;
        }

        // 配置服务器参数
        serverBootstrap
                // 设置Boss和Worker线程组
                .group(eventLoopGroupBoss, eventLoopGroupWorker)
                // 设置服务器通道类型
                .channel(SystemUtil.useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                // 配置服务器端TCP参数
                .option(ChannelOption.SO_BACKLOG, 1024)            // 连接请求等待队列大小
                .option(ChannelOption.SO_REUSEADDR, true)          // 允许端口重用（服务重启时快速绑定端口）
                .option(ChannelOption.SO_KEEPALIVE, true)          // 启用TCP心跳机制
                // 配置客户端通道参数（child开头的选项用于客户端连接）
                .childOption(ChannelOption.TCP_NODELAY, true)      // 禁用Nagle算法，减少小数据包延迟
                .childOption(ChannelOption.SO_SNDBUF, 65535)       // 发送缓冲区大小（64KB）
                .childOption(ChannelOption.SO_RCVBUF, 65535)       // 接收缓冲区大小（64KB）
                // 设置监听端口
                .localAddress(new InetSocketAddress(config.getPort()))
                // 设置新连接的处理器初始化器
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        // 配置HTTP处理管道
                        ch.pipeline().addLast(
                                new HttpServerCodec(),                         // HTTP编解码器（处理请求和响应的编码解码）
                                new HttpObjectAggregator(config.getNetty().getMaxContentLength()),  // 聚合HTTP消息（将分片消息合并）
                                new HttpServerExpectContinueHandler(),          // 处理HTTP 100 Continue请求
                                new NettyHttpServerHandler(nettyProcessor)     // 自定义业务处理器
                        );
                    }
                });

        // 绑定端口并同步等待绑定完成
        serverBootstrap.bind().sync();
        // 设置资源泄露检测级别为高级（开发调试用，生产环境可调整）
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
        log.info("Gateway server started successfully on port: {}", this.config.getPort());
    }
    /**
     * 优雅关闭服务器
     * 释放线程资源，确保服务安全停止
     */
    @Override
    public void shutdown() {
        if (!start.get()) {
            log.warn("Netty server is not running");
            return;
        }
        // 优雅关闭Boss线程组
        if (eventLoopGroupBoss != null) {
            eventLoopGroupBoss.shutdownGracefully();
        }
        // 优雅关闭Worker线程组
        if (eventLoopGroupWorker != null) {
            eventLoopGroupWorker.shutdownGracefully();
        }
        log.info("Gateway server shutdown successfully");
    }
    /**
     * 获取服务器启动状态
     *
     * @return 若服务器已启动则返回true，否则返回false
     */
    @Override
    public boolean isStarted() {
        return start.get();
    }
}
