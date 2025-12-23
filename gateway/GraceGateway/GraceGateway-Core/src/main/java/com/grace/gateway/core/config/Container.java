package com.grace.gateway.core.config;

import com.grace.gateway.config.config.Config;
import com.grace.gateway.core.netty.NettyHttpClient;
import com.grace.gateway.core.netty.NettyHttpServer;
import com.grace.gateway.core.netty.processor.NettyCoreProcessor;

import java.util.concurrent.atomic.AtomicBoolean;
/**
 * 网关容器类，实现LifeCycle接口，负责管理网关核心组件的生命周期（启动、关闭）
 * 是网关服务的"容器"，统一协调Netty服务器和客户端的启动与停止
 */
public class Container implements LifeCycle {

    /**
     * Netty HTTP服务器实例
     * 负责监听客户端请求、处理路由转发等核心网关功能
     */
    private final NettyHttpServer nettyHttpServer;

    /**
     * Netty HTTP客户端实例
     * 负责网关向后端服务发起请求（如反向代理时调用上游服务）
     */
    private final NettyHttpClient nettyHttpClient;

    /**
     * 启动状态标记（原子布尔值，保证多线程环境下的线程安全）
     * 用于控制容器启动/关闭的原子性，避免重复操作
     */
    private final AtomicBoolean start = new AtomicBoolean(false);


    /**
     * 构造方法：初始化网关核心组件
     * @param config 网关全局配置对象（包含端口、超时时间等配置信息）
     */
    public Container(Config config) {
        // 初始化Netty HTTP服务器，传入配置和核心处理器（负责请求处理逻辑）
        this.nettyHttpServer = new NettyHttpServer(config, new NettyCoreProcessor());
        // 初始化Netty HTTP客户端，传入配置（如连接池大小、超时设置等）
        this.nettyHttpClient = new NettyHttpClient(config);
    }

    /**
     * 启动网关容器，触发所有核心组件的启动
     * 实现LifeCycle接口的start方法，保证启动操作仅执行一次
     */
    @Override
    public void start() {
        // 使用CAS操作（compareAndSet）确保启动方法仅执行一次：
        // 当start为false时设置为true，返回true表示首次启动；否则直接返回
        if (!start.compareAndSet(false, true)) return;

        // 启动Netty HTTP服务器（开始监听端口，接收客户端请求）
        nettyHttpServer.start();
        // 启动Netty HTTP客户端（初始化连接池等资源，准备向后端服务发起请求）
        nettyHttpClient.start();
    }

    /**
     * 关闭网关容器，释放所有核心组件的资源
     * 实现LifeCycle接口的shutdown方法，确保仅在已启动状态下执行关闭
     */
    @Override
    public void shutdown() {
        // 仅当容器已启动（start为true）时执行关闭操作
        if (!start.get()) return;

        // 关闭Netty HTTP服务器（停止监听，释放端口和线程资源）
        nettyHttpServer.shutdown();
        // 关闭Netty HTTP客户端（释放连接池、关闭空闲连接等）
        nettyHttpClient.shutdown();
    }

    /**
     * 检查网关容器是否已启动
     * 实现LifeCycle接口的isStarted方法，返回当前启动状态
     * @return 容器启动状态（true表示已启动，false表示未启动）
     */
    @Override
    public boolean isStarted() {
        return start.get();
    }

}
