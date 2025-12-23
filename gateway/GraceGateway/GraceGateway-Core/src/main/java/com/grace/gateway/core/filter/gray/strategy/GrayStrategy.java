package com.grace.gateway.core.filter.gray.strategy;

import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.core.context.GatewayContext;

import java.util.List;

/**
 * 灰度路由策略接口
 * 定义了所有灰度策略必须实现的方法，采用策略模式设计
 * 不同的灰度发布策略（如基于IP、基于权重、基于参数等）都需实现此接口
 */
public interface GrayStrategy {

    /**
     * 判断当前请求是否应该路由到灰度实例
     *
     * @param context  网关上下文对象，包含了当前请求的所有信息
     * @param instances 服务的所有可用实例列表（包括灰度和非灰度实例）
     * @return true-应该路由到灰度实例，false-应该路由到正常实例
     */
    boolean shouldRoute2Gray(GatewayContext context, List<ServiceInstance> instances);

    /**
     * 获取当前策略的标识
     * 用于策略的注册和选择，在配置中指定使用哪种策略时会用到此标识
     *
     * @return 策略的唯一标识字符串
     */
    String mark();

}
