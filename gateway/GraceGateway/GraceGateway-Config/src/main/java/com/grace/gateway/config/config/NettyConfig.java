package com.grace.gateway.config.config;

import lombok.Data;

/**
 * Netty服务器配置类
 * 存储Netty框架相关的配置参数，用于初始化和优化Netty服务器性能
 */
@Data
public class NettyConfig {

    /**
     * Boss线程组数量
     * Boss线程主要负责接收客户端连接并将其分发给Worker线程处理
     * 默认为1，通常不需要修改，因为一个线程足以处理所有连接请求
     */
    private int eventLoopGroupBossNum = 1;

    /**
     * Worker线程组数量
     * Worker线程负责处理客户端的I/O操作和业务逻辑
     * 默认为CPU核心数的2倍，这是Netty推荐的设置，可根据实际负载调整
     */
    private int eventLoopGroupWorkerNum = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * HTTP请求的最大内容长度
     * 用于限制客户端发送的请求体大小，防止大文件上传耗尽服务器资源
     * 默认为64MB（64 * 1024 * 1024字节），可根据业务需求调整
     */
    private int maxContentLength = 64 * 1024 * 1024; // 64MB

}
