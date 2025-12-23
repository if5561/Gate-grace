package com.grace.gateway.core.context;

import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.core.filter.FilterChain;
import com.grace.gateway.core.helper.ContextHelper;
import com.grace.gateway.core.request.GatewayRequest;
import com.grace.gateway.core.response.GatewayResponse;
import io.netty.channel.ChannelHandlerContext;
import lombok.Data;

/**
 * 网关上下文类
 * 封装请求处理过程中的所有关键信息，作为过滤器链执行的载体
 * 负责协调过滤器的前置/后置处理流程，贯穿请求处理的全生命周期
 */
@Data // Lombok注解，自动生成getter、setter、toString等方法
public class GatewayContext {

    /**
     * Netty通道上下文
     * 用于操作网络通道（如发送响应、获取连接信息等）
     */
    private ChannelHandlerContext nettyCtx;

    /**
     * 请求过程中发生的异常
     * 用于在过滤器链中传递异常信息，便于统一处理
     */
    private Throwable throwable;

    /**
     * 网关请求对象
     * 封装客户端原始请求及服务调用相关信息
     */
    private GatewayRequest request;

    /**
     * 网关响应对象
     * 存储服务返回的响应数据，供后续过滤器处理或写回客户端
     */
    private GatewayResponse response;

    /**
     * 路由定义
     * 包含当前请求匹配的路由规则（如服务名、路径、过滤器配置等）
     */
    private RouteDefinition route;

    /**
     * 是否保持长连接
     * 由请求头Connection: keep-alive决定，用于控制响应后是否关闭通道
     */
    private boolean keepAlive;

    /**
     * 过滤器链
     * 包含当前请求需要执行的所有过滤器
     */
    private FilterChain filterChain;

    /**
     * 当前执行的过滤器索引
     * 用于记录过滤器链的执行进度（前置处理递增，后置处理递减）
     */
    private int curFilterIndex = 0;

    /**
     * 是否正在执行前置过滤器
     * 标记当前处理阶段（前置/后置），控制过滤器执行顺序
     */
    private boolean isDoPreFilter = true;

    /**
     * 构造方法：初始化网关上下文
     *
     * @param nettyCtx  Netty通道上下文
     * @param request   网关请求对象
     * @param route     路由定义
     * @param keepAlive 是否保持长连接
     */
    public GatewayContext(ChannelHandlerContext nettyCtx, GatewayRequest request,
                          RouteDefinition route, boolean keepAlive) {
        this.nettyCtx = nettyCtx;
        this.request = request;
        this.route = route;
        this.keepAlive = keepAlive;
    }

    /**
     * 执行过滤器链的核心方法
     * 按顺序执行过滤器的前置处理（doPreFilter），完成后切换到后置处理（doPostFilter）
     * 所有过滤器执行完毕后，触发响应写回客户端
     */
    public void doFilter() {
        // 获取过滤器链中过滤器的总数量
        int size = filterChain.size();

        if (isDoPreFilter) {
            // 阶段1：执行前置过滤器
            // 调用当前索引对应的过滤器的前置处理方法，然后索引+1
            filterChain.doPreFilter(curFilterIndex++, this);

            // 当前置过滤器全部执行完毕（索引达到过滤器总数）
            if (curFilterIndex == size) {
                // 切换到后置处理阶段
                isDoPreFilter = false;
                // 调整索引为最后一个过滤器的位置（准备从后往前执行后置处理）
                curFilterIndex--;
            }
        } else {
            // 阶段2：执行后置过滤器
            // 调用当前索引对应的过滤器的后置处理方法，然后索引-1
            filterChain.doPostFilter(curFilterIndex--, this);

            // 当后置过滤器全部执行完毕（索引小于0）
            if (curFilterIndex < 0) {
                // 通过上下文工具类将响应写回客户端
                ContextHelper.writeBackResponse(this);
            }
        }
    }

}
