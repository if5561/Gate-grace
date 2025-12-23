package com.grace.gateway.core.filter.route;

import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.Filter;
import com.grace.gateway.core.helper.ContextHelper;
import com.grace.gateway.core.helper.ResponseHelper;
import com.grace.gateway.core.resilience.Resilience;
import org.asynchttpclient.Response;

import java.util.concurrent.CompletableFuture;

import static com.grace.gateway.common.constant.FilterConstant.ROUTE_FILTER_NAME;
import static com.grace.gateway.common.constant.FilterConstant.ROUTE_FILTER_ORDER;

/**
 * 路由过滤器
 * 负责将请求路由到目标服务，并处理路由过程中的相关逻辑
 * 实现了Filter接口，是网关过滤器链中的重要环节
 */
public class RouteFilter implements Filter {

    /**
     * 前置过滤方法
     * 在请求被路由到目标服务之前执行的逻辑
     * @param context 网关上下文对象，包含了请求、路由等所有相关信息
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 从路由定义中获取弹性配置（如熔断、限流等）
        RouteDefinition.ResilienceConfig resilience = context.getRoute().getResilience();
        if (resilience.isEnabled()) { // 如果开启了弹性配置
            // 通过Resilience工具类执行请求，会应用熔断、限流等弹性策略
            Resilience.getInstance().executeRequest(context);
        } else {
            // 未开启弹性配置时，直接构建路由请求并执行
            // RouteUtil.buildRouteSupplier(context)创建请求供应商
            // get()获取异步HTTP请求
            // toCompletableFuture()转换为CompletableFuture以便异步处理
            CompletableFuture<Response> future = RouteUtil.buildRouteSupplier(context).get().toCompletableFuture();
            // 处理请求异常情况
            future.exceptionally(throwable -> {
                // 构建HTTP响应错误的网关响应
                context.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.HTTP_RESPONSE_ERROR));
                // 将响应写回到客户端
                ContextHelper.writeBackResponse(context);
                return null;
            });
        }
    }
    /**
     * 后置过滤方法
     * 在请求得到目标服务响应后执行的逻辑
     * @param context 网关上下文对象
     */
    @Override
    public void doPostFilter(GatewayContext context) {
        // 继续执行过滤器链中的下一个过滤器
        context.doFilter();
    }
    /**
     * 获取当前过滤器的标识
     * @return 路由过滤器的名称常量
     */
    @Override
    public String mark() {
        return ROUTE_FILTER_NAME;
    }
    /**
     * 获取当前过滤器的执行顺序
     * @return 路由过滤器的顺序常量
     */
    @Override
    public int getOrder() {
        return ROUTE_FILTER_ORDER;
    }
}
