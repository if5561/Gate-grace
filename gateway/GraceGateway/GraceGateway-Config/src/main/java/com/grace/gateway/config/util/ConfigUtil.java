package com.grace.gateway.config.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.grace.gateway.config.loader.ConfigLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * 配置工具类
 * 提供从YAML配置文件加载指定前缀配置并转换为Java对象的功能
 */
public class ConfigUtil {
    /**
     * Jackson的ObjectMapper实例，用于YAML文件的解析和Java对象的转换
     * 使用YAMLFactory作为构造参数，使其支持YAML格式的处理
     */
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    /**
     * 静态代码块，用于初始化ObjectMapper的配置
     * 禁用未知属性失败特性：当YAML中有Java类中不存在的属性时，不会抛出异常
     */
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    /**
     * 从YAML文件加载配置并转换为指定类型的对象
     *
     * @param filePath YAML配置文件的路径（相对于类路径）
     * @param clazz 要转换的目标Java类的Class对象
     * @param prefix 配置的前缀（例如"spring.datasource"），用于定位YAML中的子节点
     * @param <T> 泛型参数，代表目标对象的类型
     * @return 转换后的Java对象，如果文件不存在或解析失败则可能返回null或抛出异常
     * @throws RuntimeException 当IO操作或解析过程中发生错误时抛出
     */
    public static <T> T loadConfigFromYaml(String filePath, Class<T> clazz, String prefix) {
        try (InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream(filePath)) {
            // 检查输入流是否为null（文件不存在的情况）
            if (inputStream == null) return null;
            // 读取YAML文件内容并解析为根节点ObjectNode
            ObjectNode rootNode = (ObjectNode) mapper.readTree(inputStream);
            // 根据前缀获取对应的子节点
            ObjectNode subNode = getSubNode(rootNode, prefix);
            // 将子节点转换为目标类型的Java对象
            return mapper.treeToValue(subNode, clazz);
        } catch (IOException e) {
            // 将检查异常转换为非检查异常抛出
            throw new RuntimeException(e);
        }
    }
    /**
     * 根据前缀获取YAML中的子节点
     * 支持多级前缀，使用点号分隔（例如"a.b.c"会依次获取a节点下的b节点下的c节点）
     *
     * @param node 起始节点
     * @param prefix 配置前缀，多级用点号分隔
     * @return 找到的子节点，如果中途节点不存在则返回null
     */
    private static ObjectNode getSubNode(ObjectNode node, String prefix) {
        // 如果前缀为空，则直接返回当前节点
        if (prefix == null || prefix.isEmpty()) return node;
        // 将前缀按点号分割为多个键
        String[] keys = prefix.split("\\.");

        // 逐级获取子节点
        for (String key : keys) {
            // 如果当前节点为空或不存在，则返回null
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }
            // 获取下一级节点
            node = (ObjectNode) node.get(key);
        }
        return node;
    }
}
