package com.grace.gateway.core.helper;


import cn.hutool.json.JSONUtil;
import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.core.response.GatewayResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.Response;

import java.util.Objects;


/**
 * 响应转换工具类
 * 负责在网关各组件间的响应对象进行转换和封装，是响应数据流转的核心工具
 * 主要处理以下转换关系：
 * 1. 后端服务响应(Response) → 网关内部响应(GatewayResponse)
 * 2. 网关内部响应(GatewayResponse) → Netty服务端响应(FullHttpResponse)
 * 3. 错误码(ResponseCode) → 标准响应对象
 */
@Slf4j
public class ResponseHelper {

    /**
     * 将网关内部响应对象转换为Netty可发送的HTTP响应对象
     * 用于将网关处理后的结果转换为Netty服务端能直接返回给外部客户端的格式
     *
     * @param gatewayResponse 网关内部封装的响应对象
     * @return Netty的FullHttpResponse对象，包含完整的响应数据
     */
    public static FullHttpResponse buildHttpResponse(GatewayResponse gatewayResponse) {
        // 构建响应体内容（ByteBuf是Netty中用于网络传输的字节缓冲区）
        ByteBuf content;
        if (Objects.nonNull(gatewayResponse.getResponse())) {
            // 情况1：存在后端服务响应，直接使用后端响应的字节缓冲区
            content = Unpooled.wrappedBuffer(gatewayResponse.getResponse().getResponseBodyAsByteBuffer());
        } else if (gatewayResponse.getContent() != null) {
            // 情况2：网关内部生成的响应内容（如错误信息），转换为字节缓冲区
            content = Unpooled.wrappedBuffer(gatewayResponse.getContent().getBytes());
        } else {
            // 情况3：空响应，使用空字节缓冲区
            content = Unpooled.wrappedBuffer("".getBytes());
        }

        // 构建Netty的HTTP响应对象
        DefaultFullHttpResponse httpResponse;
        if (Objects.nonNull(gatewayResponse.getResponse())) {
            // 基于后端服务响应构建（复用状态码和响应头）
            httpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,  // 使用HTTP 1.1协议
                    HttpResponseStatus.valueOf(gatewayResponse.getResponse().getStatusCode()),  // 复用后端服务的状态码
                    content  // 响应体内容
            );
            // 复制后端服务的响应头（如Content-Type、Cache-Control等）
            httpResponse.headers().add(gatewayResponse.getResponse().getHeaders());
        } else {
            // 基于网关内部响应构建（适用于网关直接返回的响应，如错误提示）
            httpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    gatewayResponse.getHttpResponseStatus(),  // 使用网关定义的状态码
                    content
            );
            // 添加网关自定义响应头
            httpResponse.headers().add(gatewayResponse.getResponseHeaders());
            // 设置Content-Length头，告知客户端响应体大小
            httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
        }

        return httpResponse;
    }

    /**
     * 根据错误码构建标准HTTP响应
     * 用于网关在发生错误时（如路由不存在、权限拒绝）返回统一格式的错误响应
     *
     * @param responseCode 错误码枚举（包含状态码和错误信息）
     * @return 包含错误信息的Netty HTTP响应对象
     */
    public static FullHttpResponse buildHttpResponse(ResponseCode responseCode) {
        // 构建默认HTTP响应，包含错误码对应的状态码和消息
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                responseCode.getStatus(),  // 错误码对应的HTTP状态码（如404、500）
                Unpooled.wrappedBuffer(responseCode.getMessage().getBytes())  // 错误信息作为响应体
        );
        // 设置响应类型为JSON，字符集UTF-8
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
        // 设置响应体长度
        httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());

        return httpResponse;
    }


    /**
     * 将后端服务响应转换为网关内部响应对象
     * 用于将HTTP客户端接收到的后端服务响应封装为网关可处理的格式
     *
     * @param response 异步HTTP客户端接收到的后端服务响应
     * @return 网关内部的GatewayResponse对象
     */
    public static GatewayResponse buildGatewayResponse(Response response) {
        GatewayResponse gatewayResponse = new GatewayResponse();
        // 保存后端服务响应头
        gatewayResponse.setResponseHeaders(response.getHeaders());
        // 转换HTTP状态码（如200、404）
        gatewayResponse.setHttpResponseStatus(HttpResponseStatus.valueOf(response.getStatusCode()));
        // 保存响应体字符串
        gatewayResponse.setContent(response.getResponseBody());
        // 保存原始响应对象（便于后续提取更多信息）
        gatewayResponse.setResponse(response);

        return gatewayResponse;
    }

    /**
     * 根据错误码构建网关内部响应对象
     * 用于网关在处理过程中发生错误时，生成统一格式的内部响应
     *
     * @param code 错误码枚举
     * @return 包含错误信息的网关内部响应对象
     */
    public static GatewayResponse buildGatewayResponse(ResponseCode code) {
        GatewayResponse gatewayResponse = new GatewayResponse();
        // 设置响应类型为JSON
        gatewayResponse.addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
        // 设置错误对应的HTTP状态码
        gatewayResponse.setHttpResponseStatus(code.getStatus());
        // 将错误信息转换为JSON格式
        gatewayResponse.setContent(JSONUtil.toJsonStr(code.getMessage()));

        return gatewayResponse;
    }

    /**
     * 将业务数据转换为网关内部成功响应对象
     * 用于网关直接返回业务数据（如过滤器中直接返回结果，无需调用后端服务）
     *
     * @param data 业务数据对象（将被序列化为JSON）
     * @return 包含业务数据的网关内部响应对象
     */
    public static GatewayResponse buildGatewayResponse(Object data) {
        GatewayResponse gatewayResponse = new GatewayResponse();
        // 设置响应类型为JSON
        gatewayResponse.addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
        // 设置成功状态码（默认200 OK）
        gatewayResponse.setHttpResponseStatus(ResponseCode.SUCCESS.getStatus());
        // 将业务数据转换为JSON字符串作为响应体
        gatewayResponse.setContent(JSONUtil.toJsonStr(data));

        return gatewayResponse;
    }
}
