package com.grace.gateway.config.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.grace.gateway.config.pojo.RouteDefinition;

import java.util.Collection;

import static com.grace.gateway.common.constant.FilterConstant.GRAY_FILTER_NAME;
import static com.grace.gateway.common.constant.FilterConstant.LOAD_BALANCE_FILTER_NAME;

public class FilterUtil {

    public static RouteDefinition.FilterConfig findFilterConfigByName(Collection<RouteDefinition.FilterConfig> filterConfigs, String name) {
        if (name == null || name.isEmpty() || filterConfigs == null || filterConfigs.isEmpty()) return null;
        for (RouteDefinition.FilterConfig filterConfig : filterConfigs) {
            if (filterConfig == null || filterConfig.getName() == null) continue;
            if (filterConfig.getName().equals(name)) {
                return filterConfig;
            }
        }
        return null;
    }

    /**
     * 从过滤器配置集合中查找指定名称的过滤器配置，并转换为目标类型
     *
     * @param filterConfigs 路由配置中包含的所有过滤器配置集合（可能为 null 或空）
     * @param name          要查找的过滤器名称（如 "flowFilter"、"corsFilter" 等）
     * @param clazz         目标转换类型（过滤器配置对应的 Java 类，如 FlowFilterConfig.class）
     * @param <T>           泛型类型，与 clazz 一致，保证返回类型安全
     * @return 转换后的过滤器配置对象；若未找到对应配置或转换失败，返回 null
     */
    public static <T> T findFilterConfigByClass(
            Collection<RouteDefinition.FilterConfig> filterConfigs,
            String name,
            Class<T> clazz
    ) {
        // 1. 先根据过滤器名称从集合中查找原始配置对象（FilterConfig）
        RouteDefinition.FilterConfig filterConfig = findFilterConfigByName(filterConfigs, name);
        // 2. 若未找到对应配置，直接返回 null
        if (filterConfig == null) {
            return null;
        }
        // 3. 将原始配置中的配置信息（通常是 Map 或 JSON 结构）转换为目标类对象
        // BeanUtil.toBean：工具方法，将一种数据结构（如 Map）映射为指定类的实例
        return BeanUtil.toBean(filterConfig.getConfig(), clazz);
    }

    public static RouteDefinition.FilterConfig buildDefaultGrayFilterConfig() {
        RouteDefinition.FilterConfig filterConfig = new RouteDefinition.FilterConfig();
        filterConfig.setName(GRAY_FILTER_NAME);
        filterConfig.setEnable(true);
        filterConfig.setConfig(JSONUtil.toJsonStr(new RouteDefinition.GrayFilterConfig()));
        return filterConfig;
    }

    public static RouteDefinition.FilterConfig buildDefaultLoadBalanceFilterConfig() {
        RouteDefinition.FilterConfig filterConfig = new RouteDefinition.FilterConfig();
        filterConfig.setName(LOAD_BALANCE_FILTER_NAME);
        filterConfig.setEnable(true);
        filterConfig.setConfig(JSONUtil.toJsonStr(new RouteDefinition.LoadBalanceFilterConfig()));
        return filterConfig;
    }

}
