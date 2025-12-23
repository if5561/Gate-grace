package com.grace.gateway.config.service.impl.nacos;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grace.gateway.config.config.ConfigCenter;
import com.grace.gateway.config.config.lib.nacos.NacosConfig;
import com.grace.gateway.config.pojo.RouteDefinition;
import com.grace.gateway.config.service.ConfigCenterProcessor;
import com.grace.gateway.config.service.RoutesChangeListener;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class NacosConfigCenter implements ConfigCenterProcessor {

    // 配置中心基础配置（包含是否启用、服务地址等信息）
    private ConfigCenter configCenter;
    // Nacos配置服务客户端（用于后续获取配置、监听变更）
    private ConfigService configService;
    // 初始化状态标记（原子布尔值，确保初始化操作仅执行一次）
    private final AtomicBoolean init = new AtomicBoolean(false);

    @SneakyThrows(NacosException.class)
    public void init(ConfigCenter configCenter) {
        // 条件判断：若配置中心未启用，或已初始化（通过CAS操作保证原子性），则直接返回
        // compareAndSet(false, true)：仅当当前值为false时设置为true，防止重复初始化
        if (!configCenter.isEnabled() || !init.compareAndSet(false, true)) {
            return;
        }
        // 保存配置中心基础配置到当前实例
        this.configCenter = configCenter;
        // 创建Nacos配置服务客户端：通过构建的Properties连接Nacos服务器
        this.configService = NacosFactory.createConfigService(buildProperties(configCenter));
    }

    @SneakyThrows(NacosException.class)
    public void subscribeRoutesChange(RoutesChangeListener listener) {
        // 若配置中心未启用或未初始化完成，则直接返回（避免无效操作）
        if (!configCenter.isEnabled() || !init.get()) {
            return;
        }

        // 从配置中心获取Nacos的具体配置（如dataId、group、超时时间等）
        NacosConfig nacos = configCenter.getNacos();

        // 1. 拉取初始配置：从Nacos获取指定dataId和group的配置内容
        // 参数说明：dataId（配置唯一标识）、group（配置分组）、timeout（超时时间）
        String configJson = configService.getConfig(nacos.getDataId(), nacos.getGroup(), nacos.getTimeout());

        /*
         * 配置JSON格式示例：
         * {
         *     "routes": [
         *         {
         *             "id": "user-service-route",       // 路由唯一标识
         *             "serviceName": "user-service",    // 路由对应的服务名
         *             "uri": "/api/user/**"             // 路由匹配的URI路径
         *         }
         *     ]
         * }
         */
        log.info("初始路由配置（从Nacos获取）: \n{}", configJson);

        // 解析JSON配置：将"routes"数组转换为RouteDefinition对象列表
        List<RouteDefinition> routes = JSON.parseObject(configJson)
                .getJSONArray("routes")
                .toJavaList(RouteDefinition.class);

        // 触发初始回调：将解析后的路由列表通过监听器通知给订阅者
        listener.onRoutesChange(routes);

        // 2. 注册配置变更监听器：监听Nacos中该配置的后续变化
        configService.addListener(
                nacos.getDataId(),  // 要监听的配置ID
                nacos.getGroup(),   // 要监听的配置分组
                new Listener() {    // Nacos的配置监听器接口
                    /**
                     * 指定处理配置变更的线程池（返回null表示使用Nacos默认线程池）
                     */
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    /**
                     * 当Nacos中的配置发生变更时，会回调此方法
                     * @param configInfo 变更后的配置内容（JSON字符串）
                     */
                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        log.info("路由配置发生变更（从Nacos推送）: {}", configInfo);

                        // 解析变更后的配置为路由列表
                        List<RouteDefinition> routes = JSON.parseObject(configInfo)
                                .getJSONArray("routes")
                                .toJavaList(RouteDefinition.class);

                        // 触发变更回调：通过传入的listener通知路由变更
                        listener.onRoutesChange(routes);
                    }
                }
        );
    }

    /**
     * 构建Nacos连接所需的Properties参数
     * 将配置中心的基础配置和Nacos专有配置合并为Nacos要求的参数格式
     *
     * @param configCenter 配置中心基础配置
     * @return 符合Nacos要求的Properties对象，用于创建ConfigService
     */
    private Properties buildProperties(ConfigCenter configCenter) {
        // 创建Jackson的ObjectMapper，用于对象与Map的转换
        ObjectMapper mapper = new ObjectMapper();
        // 初始化Properties对象，存储Nacos连接参数
        Properties properties = new Properties();
        // 设置Nacos服务器地址（必填参数，从配置中心基础配置中获取）
        properties.put(PropertyKeyConst.SERVER_ADDR, configCenter.getAddress());
        // 将Nacos专有配置（如namespace、username、password等）转换为Map
        // configCenter.getNacos()返回Nacos的详细配置对象
        Map<String, Object> nacosConfigMap = mapper.convertValue(configCenter.getNacos(), Map.class);
        // 若Nacos专有配置不为空，则将其所有键值对添加到Properties中
        if (nacosConfigMap != null && !nacosConfigMap.isEmpty()) {
            properties.putAll(nacosConfigMap);
        }
        // 返回构建完成的Properties（包含服务器地址和其他Nacos配置）
        return properties;
    }

}
