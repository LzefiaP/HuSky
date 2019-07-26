package com.husky.loadbalance;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.cluster.loadbalance.AbstractLoadBalance;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WeightRoundRobinLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "weightRoundrobin";
    private final Map<Invoker,IntegerWrapper> invokerToWeightMap = new ConcurrentHashMap<Invoker,IntegerWrapper>();

    @Override
    protected synchronized <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int length = invokers.size(); // Number of invokers
        int maxWeight = 0; // The maximum weight
        int weightSum = 0;

        List<String> reqUrlList = new ArrayList<>();
        for (Invoker invoker:invokers) {
            reqUrlList.add(invoker.getUrl().toFullString());
        }
        List<String> urlCacheList = new ArrayList<>();
        Map<String,Invoker> invokerMap  = new LinkedHashMap();
        for (Map.Entry<Invoker,IntegerWrapper> entry:invokerToWeightMap.entrySet()) {
            urlCacheList.add(entry.getKey().getUrl().toFullString());
            invokerMap.put(entry.getKey().getUrl().toFullString(),entry.getKey());
        }
        //invokerToWeightMap中存在但是invokers不存在,所以要删掉
        urlCacheList.removeAll(reqUrlList);
        if(0<urlCacheList.size()){
            //删除差集
            for (String str:urlCacheList) {
                invokerToWeightMap.remove(invokerMap.get(str));
            }
        }else{
            for (int i = 0; i < length; i++) {
                int weight = getWeight(invokers.get(i), invocation);
                weightSum+=weight;
                invokerToWeightMap.putIfAbsent(invokers.get(i), new IntegerWrapper(weight,weight));
            }
        }

        Invoker maxWeightInvoker = null;
        IntegerWrapper maxWeightValue =  null;
        for (Map.Entry<Invoker, IntegerWrapper> each : invokerToWeightMap.entrySet()) {
            final Invoker<T> k = each.getKey();
            final IntegerWrapper v = each.getValue();
            // 每个invoker的当前权重要加上原始的权重
            v.setCurrentValue(v.getCurrentValue() + v.getConstantValue());
            // 保存当前权重最大的invoker
            if (maxWeightInvoker == null || maxWeight < v.getCurrentValue() ) {
                maxWeightInvoker = k ;
                maxWeightValue = v;
                maxWeight = v.getCurrentValue();
            }

        }
        // 被选中的节点权重减掉总权重
        maxWeightValue.setCurrentValue(maxWeightValue.getCurrentValue() - weightSum);
        // Round robin
        return maxWeightInvoker;
    }

    private static final class IntegerWrapper {
        private int currentValue;
        private int constantValue;

        public IntegerWrapper(int currentValue, int constantValue) {
            this.currentValue = currentValue;
            this.constantValue = constantValue;
        }

        public int getCurrentValue() {
            return currentValue;
        }

        public void setCurrentValue(int currentValue) {
            this.currentValue = currentValue;
        }

        public int getConstantValue() {
            return constantValue;
        }

        public void setConstantValue(int constantValue) {
            this.constantValue = constantValue;
        }
    }

    protected int getWeight(Invoker<?> invoker, Invocation invocation) {
        URL url = invoker.getUrl();
        int weight = url.getMethodParameter(invocation.getMethodName(), Constants.WEIGHT_KEY, Constants.DEFAULT_WEIGHT);

        //mock 读取配置
        if(20880 == invoker.getUrl().getPort()){
            weight = 20;
        }else{
            weight = 30;
        }
        if (weight > 0) {
            long timestamp = invoker.getUrl().getParameter(Constants.REMOTE_TIMESTAMP_KEY, 0L);
            if (timestamp > 0L) {
                int uptime = (int) (System.currentTimeMillis() - timestamp);
                int warmup = invoker.getUrl().getParameter(Constants.WARMUP_KEY, Constants.DEFAULT_WARMUP);
                if (uptime > 0 && uptime < warmup) {
                    weight = calculateWarmupWeight(uptime, warmup, weight);
                }
            }
        }
        return weight;
    }

    private static int calculateWarmupWeight(int uptime, int warmup, int weight) {
        int ww = (int) ((float) uptime / ((float) warmup / (float) weight));
        return ww < 1 ? 1 : (ww > weight ? weight : ww);
    }

}
