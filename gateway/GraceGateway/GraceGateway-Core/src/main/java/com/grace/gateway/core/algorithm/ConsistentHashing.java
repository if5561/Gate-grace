package com.grace.gateway.core.algorithm;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 一致性哈希算法实现
 * 用于解决分布式系统中负载均衡问题，在服务实例动态变化时最小化数据/请求的迁移
 * 核心思想是将节点和请求映射到一个虚拟的哈希环上，实现请求的稳定路由
 */
public class ConsistentHashing {
    /**
     * 每个物理节点对应的虚拟节点数量
     * 用于优化哈希分布均匀性，避免因节点数量少导致的负载不均
     */
    private final int virtualNodeNum;
    /**
     * 哈希环容器，采用TreeMap实现
     * 键：虚拟节点的哈希值（整数）
     * 值：对应的物理节点标识
     * TreeMap的有序性保证了可以快速查找哈希环上的节点
     */
    private final SortedMap<Integer, String> hashCircle = new TreeMap<>();
    /**
     * 构造函数，初始化一致性哈希环
     * @param nodes 物理节点列表（如服务实例ID）
     * @param virtualNodeNum 每个物理节点对应的虚拟节点数量
     */
    public ConsistentHashing(List<String> nodes, int virtualNodeNum) {
        this.virtualNodeNum = virtualNodeNum;
        // 初始化时将所有物理节点添加到哈希环
        for (String node : nodes) {
            addNode(node);
        }
    }
    /**
     * 添加物理节点到哈希环
     * 实际会为每个物理节点创建多个虚拟节点并添加到哈希环
     * @param node 物理节点标识（如服务实例ID）
     */
    public void addNode(String node) {
        // 为每个物理节点创建指定数量的虚拟节点
        for (int i = 0; i < virtualNodeNum; i++) {
            // 虚拟节点命名规则：物理节点标识 + 虚拟节点序号（如"instance1&&VN0"）
            String virtualNode = node + "&&VN" + i;
            // 计算虚拟节点的哈希值并放入哈希环
            hashCircle.put(getHash(virtualNode), node);
        }
    }
    /**
     * 根据键（如请求标识）获取对应的物理节点
     * 核心逻辑：在哈希环上找到第一个大于等于键哈希值的虚拟节点，对应其物理节点
     * @param key 用于路由的键（如客户端IP的哈希值）
     * @return 匹配的物理节点标识，若哈希环为空则返回null
     */
    public String getNode(String key) {
        if (hashCircle.isEmpty()) {
            return null;
        }
        // 计算键的哈希值
        int hash = getHash(key);
        // 查找哈希环上大于等于当前哈希值的所有节点（右半环）
        SortedMap<Integer, String> tailMap = hashCircle.tailMap(hash);
        // 确定目标虚拟节点的哈希值：
        // 1. 若右半环不为空，取第一个节点（最小哈希值）
        // 2. 若右半环为空（键哈希值大于所有节点哈希值），取哈希环第一个节点（循环查找）
        Integer nodeHash = tailMap.isEmpty() ? hashCircle.firstKey() : tailMap.firstKey();
        // 返回目标虚拟节点对应的物理节点
        return hashCircle.get(nodeHash);
    }
    /**
     * 计算字符串的哈希值
     * 采用FNV1_32_HASH算法，具有良好的分布性和计算效率
     * @param str 输入字符串
     * @return 32位整数哈希值（非负）
     */
    private int getHash(String str) {
        final int p = 16777619; // FNV质数
        int hash = (int) 2166136261L; // FNV偏移量

        // 迭代计算每个字符的哈希值
        for (int i = 0; i < str.length(); i++) {
            hash = (hash ^ str.charAt(i)) * p;
        }
        // 进一步混淆哈希值，增强分布性
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;
        // 确保哈希值为非负数
        if (hash < 0) {
            hash = Math.abs(hash);
        }
        return hash;
    }
}
