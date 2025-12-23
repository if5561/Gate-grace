package com.grace.gateway.core.filter;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.grace.gateway.config.manager.DynamicConfigManager;
import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.core.context.GatewayContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.grace.gateway.common.constant.FilterConstant.*;

/**
 * 过滤器链工厂
 * 负责加载所有过滤器、构建并管理针对不同服务的过滤器链
 * 核心功能：根据路由配置动态组装过滤器链，支持配置变更时自动更新
 */
@Slf4j
public class FilterChainFactory {
    /**
     * 存储所有过滤器的映射（key:过滤器标识，value:过滤器实例）
     * 用于快速查找过滤器
     */
    private static final Map<String, Filter> filterMap = new HashMap<>();
    /**
     * 存储服务与对应过滤器链的映射（key:服务名，value:该服务的过滤器链）
     * 使用ConcurrentHashMap保证并发安全，适合多线程环境
     */
    private static final Map<String, FilterChain> filterChainMap = new ConcurrentHashMap<>();
    /**
     * 记录已添加路由监听器的服务名，避免重复添加监听器
     */
    private static final Set<String> addListener = new ConcurrentHashSet<>();
    /**
     * 静态初始化块：加载所有过滤器
     * 通过Java的ServiceLoader机制自动加载实现了Filter接口的所有实现类
     */
    static {
        // 加载所有Filter接口的实现类（需在META-INF/services中配置）
        ServiceLoader<Filter> serviceLoader = ServiceLoader.load(Filter.class);
        for (Filter filter : serviceLoader) {
            // 将过滤器按其标识（mark()方法返回值）存入filterMap
            filterMap.put(filter.mark(), filter);
            log.info("load filter success: {}", filter);
        }
    }
    /**
     * 为当前请求构建过滤器链，并设置到网关上下文中
     *
     * @param ctx 网关上下文，包含路由信息等
     */
    public static void buildFilterChain(GatewayContext ctx) {
        // 从路由信息中获取服务名（一个服务对应一套过滤器链）
        String serviceName = ctx.getRoute().getServiceName();
        // 从缓存中获取或创建该服务的过滤器链
        // computeIfAbsent：若key不存在，则执行后面的函数创建值并放入map
        FilterChain filterChain = filterChainMap.computeIfAbsent(serviceName, name -> {
            // 1. 创建新的过滤器链
            FilterChain chain = new FilterChain();
            // 2. 添加前置过滤器（系统默认的前置处理过滤器）
            addPreFilter(chain);
            // 3. 添加路由配置中指定的过滤器（业务自定义过滤器）
            addFilter(chain, ctx.getRoute().getFilterConfigs());
            // 4. 添加后置过滤器（系统默认的后置处理过滤器）
            addPostFilter(chain);
            // 5. 对过滤器链进行排序（按过滤器的优先级）
            chain.sort();
            // 6. 为该服务添加路由变更监听器（配置更新时清除旧的过滤器链）
            if (!addListener.contains(serviceName)) {
                DynamicConfigManager.getInstance().addRouteListener(serviceName,
                        // 匿名实现RouteListener接口，重写changeOnRoute方法
                        newRoute ->
                        // 当路由配置变更时，移除缓存中的旧过滤器链（下次请求会重建）
                        filterChainMap.remove(newRoute.getServiceName()));
                addListener.add(serviceName);
            }
            return chain;
        });
        // 将构建好的过滤器链设置到上下文，供后续执行
        ctx.setFilterChain(filterChain);
    }
    /**
     * 添加系统默认的前置过滤器
     * 前置过滤器通常用于请求处理前的准备工作（如跨域、限流等）
     *
     * @param chain 过滤器链
     */
    private static void addPreFilter(FilterChain chain) {
        // 按顺序添加前置过滤器（顺序影响执行优先级）
        addFilterIfPresent(chain, CORS_FILTER_NAME);       // 跨域过滤器
        addFilterIfPresent(chain, FLOW_FILTER_NAME);       // 流量控制过滤器
        addFilterIfPresent(chain, GRAY_FILTER_NAME);       // 灰度发布过滤器
        addFilterIfPresent(chain, LOAD_BALANCE_FILTER_NAME);// 负载均衡过滤器
    }
    /**
     * 添加路由配置中指定的过滤器（业务自定义过滤器）
     *
     * @param chain        过滤器链
     * @param filterConfigs 路由配置中的过滤器列表
     */
    private static void addFilter(FilterChain chain, Set<RouteDefinition.FilterConfig> filterConfigs) {
        // 若配置中没有过滤器，则直接返回
        if (filterConfigs == null || filterConfigs.isEmpty()) return;
        // 遍历配置中的过滤器，添加到链中
        for (RouteDefinition.FilterConfig filterConfig : filterConfigs) {
            // 尝试添加过滤器，若不存在则打印日志
            if (!addFilterIfPresent(chain, filterConfig.getName())) {
                log.info("not found filter: {}", filterConfig.getName());
            }
        }
    }
    /**
     * 添加系统默认的后置过滤器
     * 后置过滤器通常用于请求转发、响应处理等核心逻辑
     *
     * @param chain 过滤器链
     */
    private static void addPostFilter(FilterChain chain) {
        addFilterIfPresent(chain, ROUTE_FILTER_NAME); // 路由转发过滤器
    }
    /**
     * 若过滤器存在，则添加到过滤器链中
     *
     * @param chain      过滤器链
     * @param filterName 过滤器名称（标识）
     * @return 若添加成功则返回true，否则返回false
     */
    private static boolean addFilterIfPresent(FilterChain chain, String filterName) {
        // 从filterMap中查找过滤器
        Filter filter = filterMap.get(filterName);
        if (null != filter) {
            chain.add(filter); // 添加到过滤器链
            return true;
        }
        return false;
    }

}
