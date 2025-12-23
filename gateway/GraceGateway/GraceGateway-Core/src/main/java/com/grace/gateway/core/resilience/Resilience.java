package com.grace.gateway.core.resilience;

import com.grace.gateway.common.enums.ResilienceEnum;
import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.core.context.GatewayContext;
import com.grace.gateway.core.filter.route.RouteUtil;
import com.grace.gateway.core.helper.ContextHelper;
import com.grace.gateway.core.helper.ResponseHelper;
import com.grace.gateway.core.resilience.fallback.FallbackHandler;
import com.grace.gateway.core.resilience.fallback.FallbackHandlerManager;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.asynchttpclient.Response;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 弹性能力管理器
 * 基于Resilience4j框架实现网关的弹性机制（重试、熔断、限流等）
 * 负责将各种弹性策略应用于请求处理流程，增强系统稳定性
 */
public class Resilience {
    // 单例实例，确保全局唯一的弹性策略管理器
    private static final Resilience INSTANCE = new Resilience();
    // 用于执行重试任务的调度线程池
    // 核心线程数为10，负责在重试策略触发时调度请求重发
    ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(10);

    // 私有构造方法，防止外部实例化，保证单例模式
    private Resilience() {
    }

    /**
     * 获取单例实例
     * @return 全局唯一的Resilience实例
     */
    public static Resilience getInstance() {
        return INSTANCE;
    }

    /**
     * 执行带弹性策略的请求
     * 根据路由配置中的弹性策略，对请求进行包装并执行
     * @param gatewayContext 网关上下文，包含请求信息和路由配置
     */
    public void executeRequest(GatewayContext gatewayContext) {
        // 获取当前路由的弹性策略配置（包含重试、熔断等规则）
        RouteDefinition.ResilienceConfig resilienceConfig = gatewayContext.getRoute().getResilience();
        // 获取目标服务名称（用于弹性策略的标识，如熔断的服务维度隔离）
        String serviceName = gatewayContext.getRequest().getServiceDefinition().getServiceName();

        // 构建原始请求供应器（未加任何弹性策略的基础请求逻辑）
        // 由RouteUtil提供，负责实际发送HTTP请求
        Supplier<CompletionStage<Response>> supplier = RouteUtil.buildRouteSupplier(gatewayContext);

        // 按照配置的策略顺序，依次为请求供应器添加弹性策略（装饰器模式）
        // resilienceConfig.getOrder()返回策略执行顺序列表（如先重试、再熔断、最后限流）
        for (ResilienceEnum resilienceEnum : resilienceConfig.getOrder()) {
            switch (resilienceEnum) {
                // 重试策略：当请求失败时自动重试
                case RETRY -> {
                    // 通过工厂类创建重试实例（基于配置和服务名称）
                    Retry retry = ResilienceFactory.buildRetry(resilienceConfig, serviceName);
                    if (retry != null) {
                        // 用重试策略装饰原始请求供应器
                        // 装饰后，请求失败时会按配置自动重试
                        supplier = Retry.decorateCompletionStage(retry, retryScheduler, supplier);
                    }
                }
                // 降级策略：当请求失败时执行备选逻辑
                case FALLBACK -> {
                    // 检查是否启用了降级
                    if (resilienceConfig.isFallbackEnabled()) {
                        // 保存当前装饰链的供应器引用（避免lambda中变量引用问题）
                        Supplier<CompletionStage<Response>> finalSupplier = supplier;
                        // 装饰供应器：添加异常处理逻辑
                        supplier = () ->
                                finalSupplier.get()
                                        .exceptionally(throwable -> {
                                            // 获取配置的降级处理器（如返回默认值、缓存数据等）
                                            FallbackHandler handler = FallbackHandlerManager.getHandler(resilienceConfig.getFallbackHandlerName());
                                            // 执行降级逻辑
                                            handler.handle(throwable, gatewayContext);
                                            return null;
                                        });
                    }
                }
                // 熔断策略：当服务故障比例达到阈值时，自动"跳闸"阻止请求
                case CIRCUITBREAKER -> {
                    // 创建熔断实例
                    CircuitBreaker circuitBreaker = ResilienceFactory.buildCircuitBreaker(resilienceConfig, serviceName);
                    if (circuitBreaker != null) {
                        // 用熔断策略装饰供应器
                        // 当熔断状态为OPEN时，请求会直接被拒绝
                        supplier = CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);
                    }
                }
                // 信号量隔离：限制并发请求数量（基于信号量）
                case BULKHEAD -> {
                    // 创建信号量隔离实例
                    Bulkhead bulkhead = ResilienceFactory.buildBulkHead(resilienceConfig, serviceName);
                    if (bulkhead != null) {
                        // 用信号量隔离装饰供应器
                        // 当并发数超过阈值时，新请求会被拒绝
                        supplier = Bulkhead.decorateCompletionStage(bulkhead, supplier);
                    }
                }
                // 线程池隔离：用独立线程池执行请求，避免单个服务耗尽资源
                case THREADPOOLBULKHEAD -> {
                    // 创建线程池隔离实例
                    ThreadPoolBulkhead threadPoolBulkhead = ResilienceFactory.buildThreadPoolBulkhead(resilienceConfig, serviceName);
                    if (threadPoolBulkhead != null) {
                        Supplier<CompletionStage<Response>> finalSupplier = supplier;
                        // 装饰供应器：将请求提交到隔离线程池执行
                        supplier = () -> {
                            // 将请求包装为线程池任务
                            CompletionStage<CompletableFuture<Response>> future =
                                    threadPoolBulkhead.executeSupplier(() -> finalSupplier.get().toCompletableFuture());
                            try {
                                // 等待线程池任务执行完成并返回结果
                                return future.toCompletableFuture().get();
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        };
                    }
                }
            }
        }

        // 执行最终装饰完成的请求供应器（包含所有配置的弹性策略）
        // 并处理未被降级策略捕获的异常
        supplier.get().exceptionally(throwable -> {
            // 如果未启用降级，或降级逻辑未处理异常，则返回服务不可用响应
            if (!resilienceConfig.isFallbackEnabled()) {
                gatewayContext.setThrowable(throwable);
                // 构建服务不可用的响应
                gatewayContext.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.SERVICE_UNAVAILABLE));
                // 将响应写回客户端
                ContextHelper.writeBackResponse(gatewayContext);
            }
            return null;
        });
    }

}
