package com.grace.gateway.config.manager;

import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.pojo.ServiceDefinition;
import com.grace.gateway.config.pojo.ServiceInstance;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 动态配置管理器
 * 负责缓存从配置中心拉取的路由规则、服务定义和服务实例等配置信息
 * 采用单例模式，确保配置在系统中全局一致
 */
public class DynamicConfigManager {

    // 单例实例，确保全局唯一
    private static final DynamicConfigManager INSTANCE = new DynamicConfigManager();

    // 路由规则变化监听器映射：服务名 -> 监听器列表
    // 用于在路由发生变化时通知相关监听器
    private final ConcurrentHashMap<String /* 服务名 */, List<RouteListener>> routeListenerMap = new ConcurrentHashMap<>();

    // 路由ID与路由定义的映射：路由ID -> 路由定义
    private final ConcurrentHashMap<String /* 路由id */, RouteDefinition> routeId2RouteMap = new ConcurrentHashMap<>();

    // 服务名与路由定义的映射：服务名 -> 路由定义
    private final ConcurrentHashMap<String /* 服务名 */, RouteDefinition> serviceName2RouteMap = new ConcurrentHashMap<>();

    // URI路径与路由定义的映射：URI路径 -> 路由定义
    private final ConcurrentHashMap<String /* URI路径 */, RouteDefinition> uri2RouteMap = new ConcurrentHashMap<>();

    // 服务名与服务定义的映射：服务名 -> 服务定义
    private final ConcurrentHashMap<String /* 服务名 */, ServiceDefinition> serviceDefinitionMap = new ConcurrentHashMap<>();

    // 服务实例映射：服务名 -> (实例ID -> 服务实例)
    private final ConcurrentHashMap<String /* 服务名 */, ConcurrentHashMap<String /* 实例id */, ServiceInstance>> serviceInstanceMap = new ConcurrentHashMap<>();

    /*********   单例模式实现   *********/
    // 私有构造方法，防止外部实例化
    private DynamicConfigManager() {
    }

    // 获取单例实例
    public static DynamicConfigManager getInstance() {
        return INSTANCE;
    }

    /*********   路由相关操作   *********/

    /**
     * 根据路由ID更新路由信息
     * @param id 路由ID
     * @param routeDefinition 路由定义对象
     */
    public void updateRouteByRouteId(String id, RouteDefinition routeDefinition) {
        routeId2RouteMap.put(id, routeDefinition);
    }

    /**
     * 批量更新路由信息（默认不清空现有路由）
     * @param routes 路由定义集合
     */
    public void updateRoutes(Collection<RouteDefinition> routes) {
        updateRoutes(routes, false);
    }

    /**
     * 批量更新路由信息
     * @param routes 路由定义集合
     * @param clear 是否先清空现有路由
     */
    public void updateRoutes(Collection<RouteDefinition> routes, boolean clear) {
        if (routes == null || routes.isEmpty()) return;

        // 如果需要清空，先清除所有路由映射
        if (clear) {
            routeId2RouteMap.clear();
            serviceName2RouteMap.clear();
            uri2RouteMap.clear();
        }

        // 遍历路由集合，更新所有映射
        for (RouteDefinition route : routes) {
            if (route == null) continue;
            routeId2RouteMap.put(route.getId(), route);
            serviceName2RouteMap.put(route.getServiceName(), route);
            uri2RouteMap.put(route.getUri(), route);
        }
    }

    /**
     * 根据路由ID获取路由定义
     * @param id 路由ID
     * @return 路由定义对象，不存在则返回null
     */
    public RouteDefinition getRouteById(String id) {
        return routeId2RouteMap.get(id);
    }

    /**
     * 根据服务名获取路由定义
     * @param serviceName 服务名
     * @return 路由定义对象，不存在则返回null
     */
    public RouteDefinition getRouteByServiceName(String serviceName) {
        return serviceName2RouteMap.get(serviceName);
    }

    /**
     * 获取所有URI与路由的映射关系
     * @return 包含所有URI-路由映射的Entry集合
     */
    public Set<Map.Entry<String, RouteDefinition>> getAllUriEntry() {
        return uri2RouteMap.entrySet();
    }

    /*********   服务相关操作   *********/

    /**
     * 更新服务定义
     * @param serviceDefinition 服务定义对象
     */
    public void updateService(ServiceDefinition serviceDefinition) {
        serviceDefinitionMap.put(serviceDefinition.getServiceName(), serviceDefinition);
    }

    /**
     * 根据服务名获取服务定义
     * @param name 服务名
     * @return 服务定义对象，不存在则返回null
     */
    public ServiceDefinition getServiceByName(String name) {
        return serviceDefinitionMap.get(name);
    }

    /*********   服务实例相关操作   *********/

    /**
     * 新增服务实例
     * @param serviceName 服务名
     * @param instance 服务实例对象
     */
    public void addServiceInstance(String serviceName, ServiceInstance instance) {
        // 若服务名对应的实例映射不存在则创建，然后添加实例
        serviceInstanceMap.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>())
                .put(instance.getInstanceId(), instance);
    }

    /**
     * 更新服务的所有实例（全量更新）
     * @param serviceDefinition 服务定义
     * @param newInstances 新的服务实例集合
     */
    public void updateInstances(ServiceDefinition serviceDefinition, Set<ServiceInstance> newInstances) {
        // 获取或创建服务对应的实例映射
        ConcurrentHashMap<String, ServiceInstance> oldInstancesMap = serviceInstanceMap
                .computeIfAbsent(serviceDefinition.getServiceName(), k -> new ConcurrentHashMap<>());

        // 清空旧实例，添加新实例
        oldInstancesMap.clear();
        for (ServiceInstance newInstance : newInstances) {
            oldInstancesMap.put(newInstance.getInstanceId(), newInstance);
        }
    }

    /**
     * 移除服务实例
     * @param serviceName 服务名
     * @param instance 要移除的服务实例
     */
    public void removeServiceInstance(String serviceName, ServiceInstance instance) {
        serviceInstanceMap.compute(serviceName, (k, v) -> {
            // 若实例映射不存在或实例不存在，则直接返回
            if (v == null || v.get(instance.getInstanceId()) == null) return v;
            // 移除指定实例
            v.remove(instance.getInstanceId());
            return v;
        });
    }

    /**
     * 根据服务名获取所有服务实例
     * @param serviceName 服务名
     * @return 该服务的所有实例映射，不存在则返回null
     */
    public Map<String, ServiceInstance> getInstancesByServiceName(String serviceName) {
        return serviceInstanceMap.get(serviceName);
    }

    /*********   路由监听相关操作   *********/

    /**
     * 为指定服务添加路由监听器
     * @param serviceName 服务名
     * @param listener 路由监听器
     */
    public void addRouteListener(String serviceName, RouteListener listener) {
        // 若服务名对应的监听器列表不存在则创建，然后添加监听器
        routeListenerMap.computeIfAbsent(serviceName, key -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    /**
     * 当路由发生变化时，通知该路由对应的所有监听器
     * @param routeDefinition 变化后的路由定义
     */
    public void changeRoute(RouteDefinition routeDefinition) {
        // 获取该服务对应的所有监听器
        List<RouteListener> routeListeners = routeListenerMap.get(routeDefinition.getServiceName());
        if (routeListeners == null || routeListeners.isEmpty()) return;

        // 通知每个监听器路由已变化
        for (RouteListener routeListener : routeListeners) {
            routeListener.changeOnRoute(routeDefinition);
        }
    }

    // 注意：这里省略了RouteListener接口的定义，该接口应包含changeOnRoute方法
}
