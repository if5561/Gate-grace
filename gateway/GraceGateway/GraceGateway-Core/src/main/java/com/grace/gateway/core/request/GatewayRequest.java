package com.grace.gateway.core.request;

import com.alibaba.nacos.common.utils.StringUtils;
import com.grace.gateway.common.constant.HttpConstant;
import com.grace.gateway.config.pojo.ServiceDefinition;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import lombok.Data;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.grace.gateway.common.constant.BasicConstant.DATE_DEFAULT_FORMATTER;

/**
 * 网关请求封装类
 * 用于统一管理和传递网关处理过程中的所有请求相关信息
 * 包含原始请求数据、处理过程中的修改信息以及辅助方法
 */
@Data  // Lombok注解，自动生成getter、setter、toString等方法
public class GatewayRequest {

    /**
     * 请求流水号
     * 用于唯一标识一次请求，便于日志追踪和问题排查
     */
    private final String id;

    /**
     * 服务定义
     * 关联的后端服务元数据信息
     */
    private final ServiceDefinition serviceDefinition;

    /**
     * 请求进入网关时间
     * 用于计算请求处理耗时
     */
    private final long beginTime;

    /**
     * 字符集
     * 请求数据的编码方式
     */
    private final Charset charset;

    /**
     * 客户端的IP
     * 主要用于做流控、黑白名单等安全控制
     */
    private final String clientIp;

    /**
     * 请求的地址：IP:port
     * 原始请求的目标主机地址
     */
    private final String host;

    /**
     * 请求的路径   /XXX/XXX/XX
     * URI中的路径部分，不包含查询参数
     */
    private final String path;

    /**
     * URI：统一资源标识符
     * 完整格式：/XXX/XXX/XXX?attr1=value&attr2=value2
     */
    private final String uri;

    /**
     * 请求方法 POST/PUT/GET等
     * HTTP方法类型
     */
    private final HttpMethod method;

    /**
     * 请求的格式
     * 对应Content-Type头信息，如application/json
     */
    private final String contentType;

    /**
     * 请求头信息
     * 存储所有HTTP请求头
     */
    private final HttpHeaders headers;

    /**
     * 参数解析器
     * 用于解析URI中的查询参数
     */
    private final QueryStringDecoder queryStringDecoder;

    /**
     * Netty原始HTTP请求对象
     * 保存底层原始请求数据
     */
    private final FullHttpRequest fullHttpRequest;

    /**
     * 下游请求构建器
     * 用于构建转发到后端服务的请求
     */
    private final RequestBuilder requestBuilder;

    /**
     * 请求体
     * POST等方法的请求正文内容
     */
    private String body;

    /**
     * 请求Cookie映射
     * 解析后的Cookie键值对
     */
    private Map<String, io.netty.handler.codec.http.cookie.Cookie> cookieMap;

    /**
     * POST请求参数集合
     * 解析后的POST表单参数
     */
    private Map<String, List<String>> postParameters;

    /**
     * 发给下游的协议前缀
     * 默认为http://，可修改为https://等
     */
    private String modifyScheme;

    /**
     * 发给下游的主机地址
     * 可修改为后端服务的实际地址，用于路由转发
     */
    private String modifyHost;

    /**
     * 发给下游的路径
     * 可修改为后端服务的实际路径，用于路径重写
     */
    private String modifyPath;

    /**
     * 是否灰度请求标记
     * 标识当前请求是否需要走灰度发布流程
     */
    private boolean isGray;

    /**
     * 构造方法，初始化网关请求对象
     * @param serviceDefinition 服务定义
     * @param charset 字符集
     * @param clientIp 客户端IP
     * @param host 主机地址
     * @param uri 请求URI
     * @param method HTTP方法
     * @param contentType 内容类型
     * @param headers 请求头
     * @param fullHttpRequest Netty原始请求对象
     */
    public GatewayRequest(ServiceDefinition serviceDefinition, Charset charset, String clientIp, String host, String uri, HttpMethod method, String contentType, HttpHeaders headers, FullHttpRequest fullHttpRequest) {
        // 生成唯一请求ID：当前时间+UUID
        this.id = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_DEFAULT_FORMATTER)) + "---" + UUID.randomUUID();
        this.serviceDefinition = serviceDefinition;
        this.beginTime = System.currentTimeMillis();  // 记录请求进入时间
        this.charset = charset;
        this.clientIp = clientIp;
        this.host = host;
        this.uri = uri;
        this.method = method;
        this.contentType = contentType;
        this.headers = headers;
        this.fullHttpRequest = fullHttpRequest;

        // 初始化参数解析器，用于解析URI中的查询参数
        this.queryStringDecoder = new QueryStringDecoder(uri, charset);
        this.path = queryStringDecoder.path();  // 从URI中提取路径部分

        // 初始化转发地址相关参数，默认使用原始请求的地址信息
        this.modifyHost = host;
        this.modifyPath = path;
        this.modifyScheme = HttpConstant.HTTP_PREFIX_SEPARATOR;  // 默认http://

        // 初始化下游请求构建器
        this.requestBuilder = new RequestBuilder();
        this.requestBuilder.setMethod(method.name());  // 设置HTTP方法
        this.requestBuilder.setHeaders(headers);       // 设置请求头
        this.requestBuilder.setQueryParams(queryStringDecoder.parameters());  // 设置查询参数

        // 处理请求体
        ByteBuf contentBuffer = fullHttpRequest.content();
        if (Objects.nonNull(contentBuffer)) {
            this.requestBuilder.setBody(contentBuffer.nioBuffer());  // 设置请求体
            contentBuffer.release();  // 释放Netty缓冲区
        }
    }

    /**
     * 获取指定名称的Cookie
     * @param name Cookie名称
     * @return Cookie对象，不存在则返回null
     */
    public io.netty.handler.codec.http.cookie.Cookie getCookie(String name) {
        // 延迟初始化Cookie映射
        if (cookieMap == null) {
            cookieMap = new HashMap<>();
            // 从请求头中获取Cookie字符串
            String cookieStr = headers.get(HttpHeaderNames.COOKIE);
            if (StringUtils.isBlank(cookieStr)) {
                return null;
            }
            // 解码Cookie字符串为Cookie集合
            Set<io.netty.handler.codec.http.cookie.Cookie> cookies = ServerCookieDecoder.STRICT.decode(cookieStr);
            for (io.netty.handler.codec.http.cookie.Cookie cookie : cookies) {
                cookieMap.put(name, cookie);  // 存入映射（注意：此处原代码可能有问题，应该使用cookie.getName()作为key）
            }
        }
        return cookieMap.get(name);
    }

    /**
     * 构建下游服务请求对象
     * @return 可用于发送的请求对象
     */
    public Request build() {
        // 拼接完整URL：协议前缀+主机+路径
        return requestBuilder.setUrl(modifyScheme + modifyHost + modifyPath).build();
    }

}
