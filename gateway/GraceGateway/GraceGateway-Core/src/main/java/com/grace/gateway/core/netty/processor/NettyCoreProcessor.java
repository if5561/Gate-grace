package com.grace.gateway.core.netty.processor;


import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.common.exception.GatewayException;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.FilterChainFactory;
import com.grace.gateway.core.helper.ContextHelper;
import com.grace.gateway.core.helper.ResponseHelper;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关核心处理器
 * 实现 NettyProcessor 接口，负责处理 HTTP 请求的完整生命周期：
 * 1. 构建网关上下文
 * 2. 执行过滤器链
 * 3. 处理异常并返回响应
 * 4. 释放资源
 */
@Slf4j
public class NettyCoreProcessor implements NettyProcessor {

    /**
     * 处理 Netty 通道中的 HTTP 请求
     * @param ctx 通道处理器上下文，包含当前通道的信息和操作方法
     * @param request 完整的 HTTP 请求对象（包含请求行、头、体）
     */

//    ChannelHandlerContext ctx
//    全称：通道处理器上下文
//    作用：作为当前网络通道（Channel）与处理器（ChannelHandler）之间的桥梁，封装了与网络连接相关的所有信息和操作方法。
//    核心功能：
//    操作通道：通过 ctx.channel() 获取当前网络连接的通道（Channel），可执行读写、关闭等操作（如 ctx.writeAndFlush(response) 发送响应）。
//    上下文传递：在处理器链（ChannelPipeline）中传递数据和事件（如将请求传递给下一个处理器）。
//    获取环境信息：包含通道的配置（ctx.channel().config()）、事件循环组（EventLoop）等底层信息。
//            2. FullHttpRequest request
//    全称：完整的 HTTP 请求对象（Netty 定义的类，属于 io.netty.handler.codec.http 包）
//    作用：封装了客户端发送的 HTTP 请求的全部内容，包括请求行、请求头、请求体。
//    核心信息：
//    请求行：通过 request.uri() 获取请求路径（如 /api/user），request.method() 获取请求方法（如 GET/POST）。
//    请求头：通过 request.headers() 获取所有头信息（如 Content-Type、Connection 等）。
//    请求体：通过 request.content() 获取请求体内容（如 POST 请求中的表单数据或 JSON 数据）。
    @Override
    public void process(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            // 1. 构建网关上下文（封装请求、通道等信息，作为过滤器链的传递载体）
            GatewayContext gatewayContext = ContextHelper.buildGatewayContext(request, ctx);
            // 2. 构建过滤器链（根据上下文初始化并编排需要执行的过滤器）
            FilterChainFactory.buildFilterChain(gatewayContext);
            // 3. 执行过滤器链（依次执行前置过滤器、目标服务调用、后置过滤器等）
            gatewayContext.doFilter();
        } catch (GatewayException e) {
            // 处理已知网关异常（如路由不存在、权限不足等）
            log.error("处理错误 {} {}", e.getCode(), e.getCode().getMessage());
            // 构建异常响应（根据错误码生成对应的 HTTP 响应）
            FullHttpResponse httpResponse = ResponseHelper.buildHttpResponse(e.getCode());
            // 写入响应并释放资源
            doWriteAndRelease(ctx, request, httpResponse);
        } catch (Throwable t) {
            // 处理未知异常（如 NPE、IO 异常等）
            log.error("处理未知错误", t);
            // 构建默认的内部错误响应（500 状态码）
            FullHttpResponse httpResponse = ResponseHelper.buildHttpResponse(ResponseCode.INTERNAL_ERROR);
            // 写入响应并释放资源
            doWriteAndRelease(ctx, request, httpResponse);
        }
    }
    /**
     * 写入 HTTP 响应到通道，并释放请求资源
     * @param ctx 通道处理器上下文
     * @param request 需要释放的 HTTP 请求对象
     * @param httpResponse 需要发送的 HTTP 响应对象
     */
    private void doWriteAndRelease(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse httpResponse) {
        // 1. 将响应写入通道并刷新（立即发送）
        // 2. 添加监听器：响应发送完成后关闭通道（避免通道资源泄漏）
        ctx.writeAndFlush(httpResponse)
                .addListener(ChannelFutureListener.CLOSE);
        // 释放请求对象的资源（Netty 中基于引用计数管理内存，需手动释放避免内存泄漏）
        ReferenceCountUtil.release(request);
    }

}
