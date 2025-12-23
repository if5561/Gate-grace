package com.grace.gateway.config.manager;

import com.grace.gateway.config.pojo.RouteDefinition;

public interface RouteListener {
    // 路由配置变更时的回调方法
    // 参数：变更后的新路由配置（RouteDefinition）
    void changeOnRoute(RouteDefinition routeDefinition);

}
