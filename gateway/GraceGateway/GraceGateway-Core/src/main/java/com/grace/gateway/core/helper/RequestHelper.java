package com.grace.gateway.core.helper;

import com.alibaba.nacos.common.utils.StringUtils;
import com.grace.gateway.config.pojo.ServiceDefinition;
import com.grace.gateway.core.request.GatewayRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.asynchttpclient.Request;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.grace.gateway.common.constant.HttpConstant.HTTP_FORWARD_SEPARATOR;

/**
 * 请求转换工具类
 * 负责在不同层级的请求对象之间进行转换和信息提取，是网关各组件间数据流转的桥梁
 * 主要功能：
 * 1. 将Netty服务端接收的HTTP请求转换为网关内部统一的请求对象
 * 2. 将网关内部请求对象转换为HTTP客户端可执行的请求对象
 * 3. 提取客户端真实IP地址
 */
public class RequestHelper {

    /**
     * 构建网关内部统一的请求对象
     * 将Netty接收的HTTP请求转换为网关上下文所需的GatewayRequest，便于后续过滤和转发处理
     *
     * @param serviceDefinition 目标服务定义（包含服务地址、端口等元信息）
     * @param fullHttpRequest   Netty接收的完整HTTP请求对象（包含请求行、头、体）
     * @param ctx               通道处理器上下文（用于获取客户端连接信息）
     * @return 封装了完整请求信息的GatewayRequest对象
     */
    public static GatewayRequest buildGatewayRequest(ServiceDefinition serviceDefinition,
                                                     FullHttpRequest fullHttpRequest,
                                                     ChannelHandlerContext ctx) {
        // 获取HTTP请求头信息
        HttpHeaders headers = fullHttpRequest.headers();
        // 从请求头中获取Host信息（如域名或IP:端口）
        String host = headers.get(HttpHeaderNames.HOST);
        // 获取HTTP请求方法（如GET、POST）
        HttpMethod method = fullHttpRequest.method();
        // 获取请求URI（包含路径和查询参数）
        String uri = fullHttpRequest.uri();
        // 获取客户端真实IP地址
        String clientIp = getClientIp(ctx, fullHttpRequest);
        // 获取请求的MIME类型（如application/json、application/x-www-form-urlencoded）
        String contentType = HttpUtil.getMimeType(fullHttpRequest) == null ? null :
                HttpUtil.getMimeType(fullHttpRequest).toString();
        // 获取请求的字符集（默认UTF-8）
        Charset charset = HttpUtil.getCharset(fullHttpRequest, StandardCharsets.UTF_8);

        // 构建并返回网关内部请求对象
        return new GatewayRequest(serviceDefinition, charset, clientIp, host, uri, method,
                contentType, headers, fullHttpRequest);
    }

    /**
     * 将网关内部请求对象转换为HTTP客户端可执行的请求对象
     * 适配异步HTTP客户端（AsyncHttpClient）的请求格式，用于向后端服务发起请求
     *
     * @param gatewayRequest 网关内部统一请求对象
     * @return 可被AsyncHttpClient执行的Request对象
     */
    public static Request buildHttpClientRequest(GatewayRequest gatewayRequest) {
        // 委托GatewayRequest内部实现转换逻辑，封装细节
        return gatewayRequest.build();
    }

    /**
     * 获取客户端真实IP地址
     * 优先从转发头（如X-Forwarded-For）中提取，否则直接从连接信息中获取
     *
     * @param ctx     通道处理器上下文（包含连接的远程地址信息）
     * @param request HTTP请求对象（用于获取转发头信息）
     * @return 客户端真实IP地址
     */
    private static String getClientIp(ChannelHandlerContext ctx, FullHttpRequest request) {
        // 从请求头中获取转发链信息（如X-Forwarded-For）
        String xForwardedValue = request.headers().get(HTTP_FORWARD_SEPARATOR);

        String clientIp = null;
        // 如果存在转发头信息，则解析第一个IP作为客户端真实IP
        if (StringUtils.isNotEmpty(xForwardedValue)) {
            // 转发头格式通常为"client, proxy1, proxy2"，按逗号分隔
            List<String> values = Arrays.asList(xForwardedValue.split(", "));
            if (values.size() >= 1 && StringUtils.isNotBlank(values.get(0))) {
                clientIp = values.get(0);
            }
        }
        // 如果转发头中未获取到IP，则从连接的远程地址中提取
        if (clientIp == null) {
            // 获取远程连接的Socket地址
            InetSocketAddress inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            // 提取IP地址（如192.168.1.1）
            clientIp = inetSocketAddress.getAddress().getHostAddress();
        }
        return clientIp;
    }

}
