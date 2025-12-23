package com.grace.gateway.core.filter.loadbalance.strategy;
import com.grace.gateway.config.pojo.ServiceInstance;
import com.grace.gateway.core.context.GatewayContext;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import static com.grace.gateway.common.constant.LoadBalanceConstant.WEIGHT_LOAD_BALANCE_STRATEGY;

/**
 * 权重负载均衡策略实现类
 * 基于服务实例的权重分配请求，权重越高的实例被选中的概率越大
 */
public class WeightLoadBalanceStrategy implements LoadBalanceStrategy {

    /**
     * 根据权重选择合适的服务实例
     * @param context 网关上下文对象，包含当前请求的相关信息
     * @param instances 可用的服务实例列表
     * @return 选中的服务实例，若没有合适实例则返回null
     */
    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        // 计算所有服务实例的权重总和
        // 使用stream流将每个实例的权重转换为int并求和
        int totalWeight = instances.stream().mapToInt(ServiceInstance::getWeight).sum();
        // 如果总权重小于等于0，说明没有可用的有效实例，返回null
        if (totalWeight <= 0) return null;
        // 生成一个0到总权重之间的随机数
        // 使用ThreadLocalRandom确保线程安全，避免多线程环境下的竞争问题
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);

        // 遍历服务实例，用随机数减去每个实例的权重
        // 当随机数小于0时，返回当前实例，实现按权重比例的随机选择
        for (ServiceInstance instance : instances) {
            randomWeight -= instance.getWeight();
            if (randomWeight < 0) return instance;
        }
        // 理论上不会执行到这里，作为安全兜底返回null
        return null;
    }
    /**
     * 返回当前负载均衡策略的标识
     * @return 权重负载均衡策略的常量标识
     */
    @Override
    public String mark() {
        return WEIGHT_LOAD_BALANCE_STRATEGY;
    }
}
