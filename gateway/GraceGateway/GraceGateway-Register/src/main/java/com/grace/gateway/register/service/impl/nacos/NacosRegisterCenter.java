package com.grace.gateway.register.service.impl.nacos;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingMaintainFactory;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grace.gateway.config.config.Config;
import com.grace.gateway.config.config.RegisterCenter;
import com.grace.gateway.config.pojo.ServiceDefinition;
import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.register.service.RegisterCenterListener;
import com.grace.gateway.register.service.RegisterCenterProcessor;
import com.grace.gateway.register.util.NetUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Nacos 注册中心处理器实现类
 * 负责与 Nacos 注册中心进行交互，包括初始化连接、注册网关实例及服务元数据、订阅服务变更等功能
 */
@Slf4j
public class NacosRegisterCenter implements RegisterCenterProcessor {

    /**
     * 注册中心配置，包含注册中心地址、Nacos 相关配置等信息
     */
    private Config config;

    /**
     * NamingService 用于操作服务实例（注册、发现、订阅等）
     */
    private NamingService namingService;

    /**
     * NamingMaintainService 用于维护服务元数据（如服务定义、元信息更新等）
     */
    private NamingMaintainService namingMaintainService;

    /**
     * 注册中心监听器，用于接收服务变更事件并回调业务逻辑
     */
    private RegisterCenterListener listener;

    /**
     * 初始化标记，保证 init 方法仅执行一次
     */
    private final AtomicBoolean init = new AtomicBoolean(false);

    /**
     * 初始化方法，建立与 Nacos 注册中心的连接，注册网关自身实例及服务元数据
     *
     * @param config 全局配置对象，包含注册中心、网关端口等信息
     */
    @SneakyThrows(Exception.class)
    @Override
    public void init(Config config) {
        // 利用 AtomicBoolean 保证 init 方法仅执行一次，避免重复初始化
        if (!init.compareAndSet(false, true)) {
            return;
        }
        this.config = config;

        // 获取 Nacos 配置的分组信息
        String group = config.getRegisterCenter().getNacos().getGroup();

        // 构建 Nacos 连接所需的配置参数
        Properties properties = buildProperties(config.getRegisterCenter());
        // 创建 NamingService 和 NamingMaintainService 实例，用于操作注册中心
        namingService = NamingFactory.createNamingService(properties);
        namingMaintainService = NamingMaintainFactory.createMaintainService(properties);

        // 将网关自身注册到 Nacos 注册中心
        Instance instance = new Instance();
        // 生成实例唯一标识（IP + 端口）
        instance.setInstanceId(NetUtil.getLocalIp() + ":" + config.getPort());
        instance.setIp(NetUtil.getLocalIp());
        instance.setPort(config.getPort());
        // 执行注册操作，参数：服务名（取自 config）、分组、实例信息
        namingService.registerInstance(config.getName(), group, instance);
        log.info("gateway instance register: {}", instance);

        // 设置网关服务的元数据信息
        // 将 ServiceDefinition 对象转成 Map（通过 BeanUtils.describe），用于设置服务元数据
        Map<String, String> serviceInfo = BeanUtils.describe(new ServiceDefinition(config.getName()));
        // 更新服务元数据，参数：服务名、分组、保护阈值（0 表示不开启保护阈值）、元数据 Map
      //  namingMaintainService.updateService(config.getName(), group, 0, serviceInfo);
        log.info("gateway service meta register: {}", serviceInfo);
    }

    /**
     * 订阅服务变更事件，设置监听器并启动定时任务周期性拉取服务信息
     *
     * @param listener 注册中心监听器，接收服务变更事件回调
     */
    @Override
    public void subscribeServiceChange(RegisterCenterListener listener) {
        this.listener = listener;

        // 启动定时线程池，每隔 10 秒执行一次 doSubscribeAllServices 方法，用于订阅服务并监听变更
        Executors.newScheduledThreadPool(1, new NameThreadFactory("doSubscribeAllServices")).
                scheduleWithFixedDelay(this::doSubscribeAllServices, 0, 10, TimeUnit.SECONDS);
    }

    /**
     * 构建 Nacos 连接所需的配置参数
     *
     * @param registerCenter 注册中心配置对象，包含地址、Nacos 专属配置等
     * @return 封装好的 Properties 对象，用于创建 Nacos 客户端
     */
    private Properties buildProperties(RegisterCenter registerCenter) {
        ObjectMapper mapper = new ObjectMapper();
        Properties properties = new Properties();
        // 设置 Nacos 注册中心地址
        properties.put(PropertyKeyConst.SERVER_ADDR, registerCenter.getAddress());
        // 将 Nacos 专属配置转成 Map 并合并到 properties 中
        Map map = mapper.convertValue(registerCenter.getNacos(), Map.class);
        if (map != null && !map.isEmpty()) {
            properties.putAll(map);
        }
        return properties;
    }

    /**
     * 周期性执行的方法，用于订阅所有服务并监听变更
     * 主要逻辑：拉取注册中心的服务列表，订阅未订阅过的服务，注册监听器接收变更
     */
    private void doSubscribeAllServices() {
        try {
            String group = config.getRegisterCenter().getNacos().getGroup();

            // 获取当前已订阅的服务集合，转成服务名 Set（用于过滤已订阅服务）
            Set<String> subscribeServiceSet = namingService.getSubscribeServices().stream()
                    .map(ServiceInfo::getName)
                    .collect(Collectors.toSet());

            int pageNo = 1; // 分页查询页码，从第 1 页开始
            int pageSize = 100; // 每页查询数量

            // 分页拉取注册中心的服务列表
            List<String> serviceList = namingService.getServicesOfServer(pageNo, pageSize, group).getData();

            // 循环处理分页数据，直到拉取不到服务为止
            while (CollectionUtils.isNotEmpty(serviceList)) {
                for (String serviceName : serviceList) {
                    // 跳过已订阅的服务，避免重复订阅
                    if (subscribeServiceSet.contains(serviceName)) {
                        continue;
                    }

                    // 创建 Nacos 事件监听器，用于接收服务变更事件
                    EventListener eventListener = new NacosRegisterListener();
                    // 主动触发一次事件，用于处理首次订阅时的初始数据同步
                    eventListener.onEvent(new NamingEvent(serviceName, null));
                    // 订阅服务，当服务发生变更（实例上下线、元数据变更等）时，触发监听器
                    namingService.subscribe(serviceName, group, eventListener);
                    log.info("subscribe a service, ServiceName: {} Group: {}", serviceName, group);
                }
                // 查询下一页服务列表
                serviceList = namingService.getServicesOfServer(++pageNo, pageSize, group).getData();
            }
        } catch (Exception e) {
            // 捕获异常，避免定时任务因异常终止
            log.error("subscribe services from nacos occur exception: {}", e.getMessage(), e);
        }
    }

    /**
     * Nacos 注册中心事件监听器内部类
     * 接收 Nacos 服务变更事件，转换为自定义的服务定义和实例信息，回调业务监听器
     */
    private class NacosRegisterListener implements EventListener {

        @SneakyThrows(NacosException.class)
        @Override
        public void onEvent(Event event) {
            // 只处理 NamingEvent 类型事件（服务相关变更事件）
            if (event instanceof NamingEvent namingEvent) {
                String serviceName = namingEvent.getServiceName();
                String group = config.getRegisterCenter().getNacos().getGroup();

                // 查询服务元数据信息，转换为自定义 ServiceDefinition 对象
                Service service = namingMaintainService.queryService(serviceName, group);
                ServiceDefinition serviceDefinition = new ServiceDefinition(service.getName());
                // 将服务元数据 Map 填充到 ServiceDefinition 对象中
                BeanUtil.fillBeanWithMap(service.getMetadata(), serviceDefinition, true);

                // 获取服务的所有实例信息
                List<Instance> allInstances = namingService.getAllInstances(serviceName, group);
                Set<ServiceInstance> newInstances = new HashSet<>();

                if (CollectionUtils.isNotEmpty(allInstances)) {
                    for (Instance instance : allInstances) {
                        if (instance == null) {
                            continue;
                        }
                        // 将 Nacos Instance 转换为自定义 ServiceInstance 对象
                        ServiceInstance newInstance = new ServiceInstance();
                        BeanUtil.copyProperties(instance, newInstance);
                        // 将实例元数据填充到 ServiceInstance 对象中
                        BeanUtil.fillBeanWithMap(instance.getMetadata(), newInstance, true);
                        newInstances.add(newInstance);
                    }
                }

                // 回调业务监听器，通知服务定义和实例信息变更
                listener.onInstancesChange(serviceDefinition, newInstances);
            }
        }
    }
}
