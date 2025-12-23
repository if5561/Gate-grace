package com.grace.gateway.core.resilience.fallback;

import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.helper.ContextHelper;
import com.grace.gateway.core.helper.ResponseHelper;

import static com.grace.gateway.common.constant.FallbackConstant.DEFAULT_FALLBACK_HANDLER_NAME;

/**
 * 默认降级处理器
 * 实现FallbackHandler接口，作为网关的默认降级逻辑处理类
 * 当请求执行失败且未指定其他降级处理器时，使用此类处理降级逻辑
 */
public class DefaultFallbackHandler implements FallbackHandler {

    /**
     * 处理降级逻辑的核心方法
     * 当请求触发降级条件（如服务熔断、超时、失败等）时被调用
     * @param throwable 导致降级的异常对象，包含失败原因
     * @param context 网关上下文对象，存储请求、响应等关键信息
     */
    @Override
    public void handle(Throwable throwable, GatewayContext context) {
        // 将异常信息存入上下文，便于后续跟踪和日志记录
        context.setThrowable(throwable);
        // 构建网关降级响应：使用ResponseHelper生成统一格式的响应对象
        // 响应码为GATEWAY_FALLBACK（表示网关执行了降级处理）
        context.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.GATEWAY_FALLBACK));
        // 将降级响应写回客户端，完成请求闭环
        ContextHelper.writeBackResponse(context);
    }

    /**
     * 返回当前降级处理器的标识
     * 用于在降级处理器管理器中注册和查找
     * @return 默认降级处理器的名称常量
     */
    @Override
    public String mark() {
        return DEFAULT_FALLBACK_HANDLER_NAME;
    }

}
