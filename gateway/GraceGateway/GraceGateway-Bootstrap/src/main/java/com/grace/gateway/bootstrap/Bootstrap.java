package com.grace.gateway.bootstrap;

import com.grace.gateway.config.config.Config;
import com.grace.gateway.config.loader.ConfigLoader;
import com.grace.gateway.config.manager.DynamicConfigManager;
import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.service.ConfigCenterProcessor;
import com.grace.gateway.core.config.Container;
import com.grace.gateway.register.service.RegisterCenterProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.ServiceLoader;

@Slf4j
public class Bootstrap {

    private Config config;

    private Container container;

    public static void run(String[] args) {
        new Bootstrap().start(args);
    }

    public void start(String[] args) {
        log.info("gateway bootstrap start...");

        // 加载配置
        config = ConfigLoader.load(args);
        log.info("gateway bootstrap load config: {}", config);

        // 初始化配置中心
        initConfigCenter();

        // 启动容器
        initContainer();
        container.start();

        // 初始化注册中心
        initRegisterCenter();

        // 注册钩子，优雅停机
        registerGracefullyShutdown();
    }

    private void initConfigCenter() {
        ConfigCenterProcessor configCenterProcessor = ServiceLoader.load(ConfigCenterProcessor.class).findFirst().orElseThrow(() -> {
            log.error("not found ConfigCenter impl");
            return new RuntimeException("not found ConfigCenter impl");
        });
        configCenterProcessor.init(config.getConfigCenter());
        //这里通过lambda 表达式实现了RoutesChangeListener接口，作为具体的回调逻辑。观察者模式
        configCenterProcessor.subscribeRoutesChange(newRoutes -> {
            // 步骤1：全量更新本地路由缓存
            DynamicConfigManager.getInstance().updateRoutes(newRoutes, true);
            // 步骤2：逐个通知路由变更
            for (RouteDefinition newRoute : newRoutes) {
                DynamicConfigManager.getInstance().changeRoute(newRoute);
            }
        });
    }

    private void initContainer() {
        container = new Container(config);
    }

    /**
     * 初始化服务注册中心的方法
     * 负责加载服务注册中心处理器、初始化连接，并订阅服务信息变更
     */
    private void initRegisterCenter() {
        // 1. 通过SPI机制加载服务注册中心处理器的实现类
        // ServiceLoader.load(RegisterCenterProcessor.class)：加载所有实现了RegisterCenterProcessor接口的服务
        // findFirst().orElseThrow(...)：获取第一个实现类，若未找到则抛出异常
        RegisterCenterProcessor registerCenterProcessor = ServiceLoader.load(RegisterCenterProcessor.class)
                .findFirst()
                .orElseThrow(() -> {
                    log.error("未找到服务注册中心的实现类（RegisterCenter impl）");
                    return new RuntimeException("not found RegisterCenter impl");
                });
        // 2. 初始化服务注册中心处理器
        // 传入配置对象（config），用于建立与注册中心的连接（如地址、端口、认证信息等）
        registerCenterProcessor.init(config);
        // 3. 订阅服务信息变更事件
        // 当服务定义或实例发生变化时，触发回调函数更新本地缓存
        registerCenterProcessor.subscribeServiceChange(((serviceDefinition, newInstances) -> {
            // a. 更新服务定义到动态配置管理器
            // serviceDefinition：包含服务名称、类型、配置等元信息
            DynamicConfigManager.getInstance().updateService(serviceDefinition);
            // b. 更新服务实例列表到动态配置管理器
            // newInstances：该服务最新的可用实例集合（如IP:端口、状态等）
            DynamicConfigManager.getInstance().updateInstances(serviceDefinition, newInstances);
        }));
    }

    private void registerGracefullyShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            container.shutdown();
        }));
    }

}
