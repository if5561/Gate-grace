package com.grace.gateway.core.helper;

import com.grace.gateway.config.helper.RouteResolver;
import com.grace.gateway.config.manager.DynamicConfigManager;
import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.request.GatewayRequest;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;


/**
 * 网关上下文辅助工具类
 * 负责构建网关上下文对象和处理响应写回逻辑
 */
@Slf4j
public class ContextHelper {

    /**
     * 构建网关上下文对象（GatewayContext）
     * 上下文包含了处理请求所需的所有关键信息，贯穿整个请求生命周期
     *
     * @param request HTTP请求对象（包含请求行、头、体等信息）
     * @param ctx     Netty通道上下文（用于操作网络通道）
     * @return 构建完成的网关上下文对象
     */
    public static GatewayContext buildGatewayContext(FullHttpRequest request, ChannelHandlerContext ctx) {
        // 1. 根据请求URI匹配对应的路由规则（路由解析）
        RouteDefinition route = RouteResolver.matchingRouteByUri(request.uri());
        // 2. 构建网关请求对象（封装服务信息、原始请求、通道等）
        // 从动态配置管理器中获取路由对应的服务信息，结合原始请求和通道构建GatewayRequest
        GatewayRequest gatewayRequest = RequestHelper.buildGatewayRequest(
                DynamicConfigManager.getInstance().getServiceByName(route.getServiceName()),
                request,
                ctx
        );
        // 3. 创建并返回网关上下文对象
        // 上下文包含：通道信息、网关请求、路由规则、是否保持长连接
        return new GatewayContext(
                ctx,
                gatewayRequest,
                route,
                HttpUtil.isKeepAlive(request) // 判断客户端是否要求长连接（通过HTTP头Connection: keep-alive）
        );
    }
    /**
     * 将处理结果写回到客户端（响应处理）
     * 根据上下文信息判断是长连接还是短连接，并进行相应处理
     *
     * @param context 网关上下文对象（包含响应数据和连接信息）
     */
    public static void writeBackResponse(GatewayContext context) {
        // 1. 根据上下文的响应数据构建HTTP响应对象
        FullHttpResponse httpResponse = ResponseHelper.buildHttpResponse(context.getResponse());
        // 2. 根据连接类型（长/短连接）处理响应
        if (!context.isKeepAlive()) { // 短连接：响应后关闭通道
            context.getNettyCtx()
                    .writeAndFlush(httpResponse) // 写入并刷新响应到客户端
                    .addListener(ChannelFutureListener.CLOSE); // 响应完成后关闭通道
        } else { // 长连接：保持通道打开，方便后续请求复用
            // 设置HTTP头，告知客户端保持连接
            httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            // 写入并刷新响应（不关闭通道）
            context.getNettyCtx().writeAndFlush(httpResponse);
        }
    }
}
