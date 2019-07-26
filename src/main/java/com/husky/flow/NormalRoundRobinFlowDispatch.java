package com.husky.flow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 轮询流量分配算法
 */
public class NormalRoundRobinFlowDispatch extends AbstractFlowDispatch{
    private final List<ProviderFLowConfig> algList = new ArrayList<>();
    private final ConcurrentMap<String, PositiveRecord> sequences = new ConcurrentHashMap<String, PositiveRecord>();
    public NormalRoundRobinFlowDispatch(FlowDispatch.ProviderFLowConfig... nodes) {
        super(nodes);
        algList.addAll(nodeList);
    }

    @Override
    protected ProviderFLowConfig  doSelect() {
        String key = algList.get(0).getAlgName();
        int length = algList.size(); // Number of ALG
        final LinkedHashMap<ProviderFLowConfig, IntegerWrapper> invokerToWeightMap = new LinkedHashMap<ProviderFLowConfig,IntegerWrapper>();
        int weight = 0;
        for (int i = 0; i < length; i++) {
            invokerToWeightMap.put(nodeList.get(i), new IntegerWrapper(weight));
        }
        PositiveRecord sequence = sequences.get(key);
        if (sequence == null) {
            sequences.putIfAbsent(key, new PositiveRecord());
            sequence = sequences.get(key);
        }
        int currentSequence = sequence.getAndIncrement();
        // Round robin
        return nodeList.get(currentSequence % length);
    }

    private static final class IntegerWrapper {
        private int value;

        public IntegerWrapper(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public void decrement() {
            this.value--;
        }
    }


    public static void main(String[] args) {
        /**
         * 假设有三个服务器权重配置如下：
         * server A  weight = 4 ;
         * server B  weight = 3 ;
         * server C  weight = 2 ;
         */
        ProviderFLowConfig serverA = new ProviderFLowConfig("serverA");
        ProviderFLowConfig serverB = new ProviderFLowConfig("serverB");
        ProviderFLowConfig serverC = new ProviderFLowConfig("serverC");

        int aCount = 0,bCount = 0,cCount = 0;
        int count = 2100;

        NormalRoundRobinFlowDispatch robinFlowDispatchTemp = new NormalRoundRobinFlowDispatch(serverA,serverB ,serverC);
        for (int i = 0; i < count; i++) {
            ProviderFLowConfig i1 = robinFlowDispatchTemp.select();
            if("serverA".equals(i1.getAlgName())){
                aCount++;
            }else if("serverB".equals(i1.getAlgName())){
                bCount++;
            }else if("serverC".equals(i1.getAlgName())){
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
