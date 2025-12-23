package com.grace.gateway.core.filter.cors;

import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.Filter;
import com.grace.gateway.core.helper.ContextHelper;
import com.grace.gateway.core.helper.ResponseHelper;
import com.grace.gateway.core.response.GatewayResponse;
import io.netty.handler.codec.http.HttpMethod;

import static com.grace.gateway.common.constant.FilterConstant.CORS_FILTER_NAME;
import static com.grace.gateway.common.constant.FilterConstant.CORS_FILTER_ORDER;

/**
 * 跨域资源共享（CORS）过滤器
 * 处理浏览器的跨域请求限制，通过预检请求处理和跨域响应头设置，允许跨域请求正常交互
 */
public class CorsFilter implements Filter {

    /**
     * 前置过滤逻辑：处理预检请求（OPTIONS方法）
     * 预检请求是浏览器在跨域请求前发送的探测请求，用于确认服务器是否允许实际请求
     *
     * @param context 网关上下文，包含请求和响应信息
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 判断当前请求是否为预检请求（HTTP方法为OPTIONS）
        if (HttpMethod.OPTIONS.equals(context.getRequest().getMethod())) {
            // 构建成功响应（无需转发到后端服务，直接由网关处理预检请求）
            context.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.SUCCESS));
            // 将响应直接写回客户端，结束请求流程
            ContextHelper.writeBackResponse(context);
        } else {
            // 非预检请求，继续执行后续过滤器
            context.doFilter();
        }
    }
    /**
     * 后置过滤逻辑：为实际请求的响应添加跨域相关头信息
     * 使浏览器能够识别并接受跨域响应
     *
     * @param context 网关上下文，包含请求和响应信息
     */
    @Override
    public void doPostFilter(GatewayContext context) {
        // 获取网关响应对象
        GatewayResponse gatewayResponse = context.getResponse();
        // 添加跨域响应头，允许所有来源的请求（实际生产环境应限制具体域名）
        gatewayResponse.addHeader("Access-Control-Allow-Origin", "*");
        // 允许的HTTP方法
        gatewayResponse.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        // 允许的请求头
        gatewayResponse.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        // 允许携带凭证（如Cookie）
        gatewayResponse.addHeader("Access-Control-Allow-Credentials", "true");
        // 继续执行后续过滤器
        context.doFilter();
    }
    /**
     * 返回过滤器标识名称
     * 用于过滤器链工厂识别和管理该过滤器
     *
     * @return 过滤器名称（CORS_FILTER_NAME）
     */
    @Override
    public String mark() {
        return CORS_FILTER_NAME;
    }
    /**
     * 返回过滤器执行优先级
     * 跨域处理应在限流、路由等过滤器之前执行，确保预检请求优先被处理
     *
     * @return 优先级数值（CORS_FILTER_ORDER）
     */
    @Override
    public int getOrder() {
        return CORS_FILTER_ORDER;
    }
}
