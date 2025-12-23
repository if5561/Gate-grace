package com.grace.gateway.core.filter;

import com.grace.gateway.core.context.GatewayContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 过滤器链
 * 管理一组过滤器的集合，负责过滤器的添加、排序和按顺序执行
 * 支持前置过滤（请求处理前）和后置过滤（请求处理后）逻辑
 */
@Slf4j // Lombok注解，自动生成日志对象
public class FilterChain {
    /**
     * 存储过滤器的列表，按执行顺序维护
     */
    private final List<Filter> filters = new ArrayList<>();
    /**
     * 向过滤器链添加单个过滤器
     *
     * @param filter 要添加的过滤器实例
     * @return 当前过滤器链对象（支持链式调用）
     */
    public FilterChain add(Filter filter) {
        filters.add(filter);
        return this;
    }
    /**
     * 向过滤器链批量添加过滤器
     *
     * @param filter 要添加的过滤器列表
     * @return 当前过滤器链对象（支持链式调用）
     */
    public FilterChain add(List<Filter> filter) {
        filters.addAll(filter);
        return this;
    }
    /**
     * 对过滤器链中的过滤器进行排序
     * 按过滤器的order值升序排序（order越小，优先级越高，越先执行）
     */
    public void sort() {
        // 使用Comparator比较过滤器的order值，实现排序
        filters.sort(Comparator.comparingInt(Filter::getOrder));
    }
    /**
     * 获取过滤器链中过滤器的数量
     *
     * @return 过滤器数量
     */
    public int size() {
        return filters.size();
    }
    /**
     * 执行指定索引位置的过滤器的前置处理方法
     * 前置处理通常在请求发送到目标服务前执行（如参数校验、权限检查）
     *
     * @param index   过滤器在链中的索引（从0开始）
     * @param context 网关上下文（封装请求、响应等信息）
     */
    public void doPreFilter(int index, GatewayContext context) {
        // 检查索引是否有效（在列表范围内）
        if (index < filters.size() && index >= 0) {
            // 调用对应索引的过滤器的前置处理方法
            filters.get(index).doPreFilter(context);
        }
    }
    /**
     * 执行指定索引位置的过滤器的后置处理方法
     * 后置处理通常在目标服务返回响应后执行（如响应转换、日志记录）
     *
     * @param index   过滤器在链中的索引（从0开始）
     * @param context 网关上下文（封装请求、响应等信息）
     */
    public void doPostFilter(int index, GatewayContext context) {
        // 检查索引是否有效（在列表范围内）
        if (index < filters.size() && index >= 0) {
            // 调用对应索引的过滤器的后置处理方法
            filters.get(index).doPostFilter(context);
        }
    }
}
