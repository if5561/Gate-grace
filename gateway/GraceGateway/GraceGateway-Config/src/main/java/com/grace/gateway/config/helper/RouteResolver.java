package com.grace.gateway.config.helper;

import com.grace.gateway.common.enums.ResponseCode;
import com.grace.gateway.common.exception.NotFoundException;
import com.grace.gateway.config.manager.DynamicConfigManager;
import com.grace.gateway.config.pojo.RouteDefinition;

import java.util.*;
import java.util.regex.Pattern;
/**
 * 路由解析器
 * 负责根据请求的URI匹配对应的路由规则，核心功能是找到最符合条件的路由定义
 */
public class RouteResolver {
    // 动态配置管理器单例，用于获取所有路由配置信息
    private static final DynamicConfigManager manager = DynamicConfigManager.getInstance();
    /**
     * 根据请求URI匹配对应的路由规则
     *
     * @param uri 请求的统一资源标识符（如 "/api/user/123"）
     * @return 匹配到的最优路由定义（RouteDefinition）
     * @throws NotFoundException 如果没有匹配的路由，抛出404异常
     */
    public static RouteDefinition matchingRouteByUri(String uri) {
        // 1. 从动态配置管理器获取所有路由的键值对（key:路由URI模式，value:路由定义）
        Set<Map.Entry<String, RouteDefinition>> allUriEntry = manager.getAllUriEntry();
        // 2. 存储所有匹配当前URI的路由
        List<RouteDefinition> matchedRoute = new ArrayList<>();
        // 3. 遍历所有路由，通过正则匹配筛选符合条件的路由
        for (Map.Entry<String, RouteDefinition> entry : allUriEntry) {
            // 将路由配置中的通配符"**"转换为正则表达式".*"（支持多级路径匹配）
            String regex = entry.getKey().replace("**", ".*");
            // 使用正则匹配判断当前URI是否符合路由模式
            if (Pattern.matches(regex, uri)) {
                matchedRoute.add(entry.getValue());
            }
        }
        // 4. 如果没有匹配的路由，抛出"路径未找到"异常
        if (matchedRoute.isEmpty()) {
            throw new NotFoundException(ResponseCode.PATH_NO_MATCHED);
        }
        // 5. 对匹配的路由进行排序，选择最优路由
        // 排序规则1：按路由的order（优先级）升序（值越小优先级越高）
        // 排序规则2：若order相同，按路由URI长度降序（更长的URI更具体，优先级更高）
        matchedRoute.sort(Comparator.comparingInt(RouteDefinition::getOrder));

        // 6. 从排序后的列表中获取最优路由（最小order，相同order时最长URI）
        return matchedRoute.stream()
                .min(Comparator.comparingInt(RouteDefinition::getOrder)
                        .thenComparing(route -> route.getUri().length(), Comparator.reverseOrder()))
                .orElseThrow();
    }
}
