package com.husky.flow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 权重轮询流量分配算法
 */
public class WeightRoundRobinFlowDispatch extends AbstractFlowDispatch {
    private ReentrantLock lock = new ReentrantLock() ;
    private final List<ProviderFLowConfig> nodeListSmooth = new ArrayList<>() ; // 保存权重
    public WeightRoundRobinFlowDispatch(ProviderFLowConfig ...nodes) {
        super(nodes);
        nodeListSmooth.addAll(nodeList);
    }

    @Override
    protected ProviderFLowConfig doSelect(){
        lock.lock();
        try {
            return this.selectInner() ;
        }finally {
            lock.unlock();
        }
    }

    private ProviderFLowConfig selectInner(){
        int totalWeight = 0 ;
        ProviderFLowConfig maxNode = null ;
        int maxWeight = 0 ;
        for (int i = 0; i < nodeListSmooth.size(); i++) {
            ProviderFLowConfig n = nodeListSmooth.get(i);
            totalWeight += n.getWeight() ;
            // 每个节点的当前权重要加上原始的权重
            n.setCurrentWeight(n.getCurrentWeight() + n.getWeight());
            // 保存当前权重最大的节点
            if (maxNode == null || maxWeight < n.getCurrentWeight() ) {
                maxNode = n ;
                maxWeight = n.getCurrentWeight() ;
            }
        }
        // 被选中的节点权重减掉总权重
        maxNode.setCurrentWeight(maxNode.getCurrentWeight() - totalWeight);
        return maxNode;
    }



    public static void main(String[] args) {
        /**
         * 假设有三个服务器权重配置如下：
         * server A  weight = 4 ;
         * server B  weight = 3 ;
         * server C  weight = 2 ;
         */
        ProviderFLowConfig serverA = new ProviderFLowConfig("serverA", 4);
        ProviderFLowConfig serverB = new ProviderFLowConfig("serverB", 3);
        ProviderFLowConfig serverC = new ProviderFLowConfig("serverC", 2);

        int aCount = 0,bCount = 0,cCount = 0;
        int count = 10;

        WeightRoundRobinFlowDispatch smoothWeightedRoundRobin = new WeightRoundRobinFlowDispatch(serverA,serverB ,serverC);
        for (int i = 0; i < count; i++) {
            ProviderFLowConfig i1 = smoothWeightedRoundRobin.select();
            if("serverA".equals(i1.getServerName())){
                aCount++;
            }else if("serverB".equals(i1.getServerName())){
                bCount++;
            }else if("serverC".equals(i1.getServerName())){
                cCount++;
            }
        }
        System.out.println("----Key serverA of count----"+aCount);
        System.out.println("----Key serverB of count----"+bCount);
        System.out.println("----Key serverC of count----"+cCount);
        double aRate = new BigDecimal(aCount).divide(new BigDecimal(count),10,BigDecimal.ROUND_HALF_UP).doubleValue();
        double bRate = new BigDecimal(bCount).divide(new BigDecimal(count),10,BigDecimal.ROUND_HALF_UP).doubleValue();
        double cRate = new BigDecimal(cCount).divide(new BigDecimal(count),10,BigDecimal.ROUND_HALF_UP).doubleValue();
        System.out.println("----Key serverA of Rate----"+aRate);
        System.out.println("----Key serverB of Rate----"+bRate);
        System.out.println("----Key serverC of Rate----"+cRate);
    }
}
