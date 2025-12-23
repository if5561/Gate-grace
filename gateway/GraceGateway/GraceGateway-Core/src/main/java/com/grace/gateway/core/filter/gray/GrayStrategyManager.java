package com.grace.gateway.core.filter.gray;

import com.grace.gateway.core.filter.gray.strategy.GrayStrategy;
import com.grace.gateway.core.filter.gray.strategy.ThresholdGrayStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;


/**
 * 灰度策略管理器
 * 负责加载、存储和提供所有可用的灰度策略实例
 * 采用单例模式和服务加载机制，实现策略的自动发现和管理
 */
@Slf4j
public class GrayStrategyManager {
    /**
     * 存储灰度策略的映射表
     * key: 策略标识(通过GrayStrategy.mark()获取)
     * value: 对应的灰度策略实例
     */
    private static final Map<String, GrayStrategy> strategyMap = new HashMap<>();
    /**
     * 静态初始化块
     * 在类加载时自动加载所有实现了GrayStrategy接口的策略类
     * 使用Java的ServiceLoader机制实现策略的自动发现
     */
    static {
        // 通过ServiceLoader加载所有GrayStrategy接口的实现类
        ServiceLoader<GrayStrategy> serviceLoader = ServiceLoader.load(GrayStrategy.class);
        // 遍历加载到的所有策略实例，存入映射表
        for (GrayStrategy strategy : serviceLoader) {
            strategyMap.put(strategy.mark(), strategy);
            log.info("load gray strategy success: {}", strategy);
        }
    }
    /**
     * 根据策略名称获取对应的灰度策略实例
     * 如果指定名称的策略不存在，则返回默认策略(ThresholdGrayStrategy)
     *
     * @param name 策略名称(对应GrayStrategy.mark()返回的值)
     * @return 灰度策略实例，非空
     */
    public static GrayStrategy getStrategy(String name) {
        GrayStrategy strategy = strategyMap.get(name);
        // 如果策略不存在，使用基于阈值的策略作为默认策略
        if (strategy == null)
            strategy = new ThresholdGrayStrategy();
        return strategy;
    }
}
