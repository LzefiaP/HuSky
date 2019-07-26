package com.husky.cluster;


import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.rpc.*;
import com.alibaba.dubbo.rpc.cluster.Cluster;
import com.alibaba.dubbo.rpc.cluster.Directory;
import com.alibaba.dubbo.rpc.cluster.LoadBalance;
import com.alibaba.dubbo.rpc.cluster.support.AbstractClusterInvoker;
import com.alibaba.dubbo.rpc.protocol.InvokerWrapper;
import com.alibaba.fastjson.JSONArray;
import com.husky.common.NamedThreadFactory;
import com.husky.protocol.RedisClient;
import com.husky.transport.Soil;
import com.husky.utils.NetUtils;
import com.husky.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class FailoverHuSkyCluster implements Cluster {
    private static final String ERROR_MSG = "No provider available for method %s in the service %s"
            +" from the registry %s on the consumer  %s using the dubbo version "
            +"%s,may be providers disabled or not registered or limited by traffic distribution policy ?";
    private static final Logger logger = LoggerFactory.getLogger(FailoverHuSkyCluster.class);
    private final Map<String,IntegerWrapper> integerWrapperMap = new ConcurrentHashMap<>();
    private final RedisClient redisClient = RedisClient.instance();
    //设置清理线程,清理本地失效的内存
    private final ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(1,
            new NamedThreadFactory(
                    "HuSky-cleanup",
                    true));
    {
        scheduled.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                try {
                    cleanUp();
                    logger.info("chean up done,integerWrapperMap.size:"+integerWrapperMap.size());
                } catch (Throwable t) { // Defensive fault tolerance
                    logger.error("Unexpected error occur at cleanup, cause: " + t.getMessage(), t);
                }
            }
        }, 1, 60*3, TimeUnit.SECONDS);
    }

    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
        return new AbstractClusterInvoker<T>(directory) {
            @Override
            protected Result doInvoke(Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
                List<Invoker<T>> invokerList = new ArrayList<>();
                Iterator<Invoker<T>> invokerIterator = invokers.iterator();
                while (invokerIterator.hasNext()){
                    Invoker invokerTemp = invokerIterator.next();
                    invokerList.add(new InvokerWrapper<>(invokerTemp,invokerTemp.getUrl()));
                }
                invokers = invokerList;
                synchronized (this){
                    //接口全路径
                    final String interfacePath = invokers.get(0).getInterface().getName();
                    com.husky.Result<String> resultTemp = null;
                    try{
                        //step1:读取redis中流量分配策略配置,按照配置规则进行weight赋值(赋值到Invoker.url.parameter,使用dubbo的加权轮询策略进行流量分发)
                        com.husky.Result<String> flowResult = redisClient.invoke("get", com.husky.common.Constants.REDIS_FLOW_KEY,null,null);
                        List<ProviderNode> nodes = JSONArray.parseArray(flowResult.getResult(), ProviderNode.class);
                        /*
                        nodes = new ArrayList<>();
                        nodes.add(new ProviderNode(30,30,"10.17.53.110"));
                        */
                        //use iterator of List  delete eligible element
                        if(null==nodes || nodes.size() == 0){
                            logger.error(String.format(ERROR_MSG,invocation.getMethodName(),getInterface().getName(),directory.getUrl().getAddress(),NetUtils.getLocalHost(),Version.getVersion()));
                            throw new RpcException(String.format(ERROR_MSG,invocation.getMethodName(),getInterface().getName(),directory.getUrl().getAddress(),NetUtils.getLocalHost(),Version.getVersion()));
                        }
                        Iterator<ProviderNode> iterator = nodes.iterator();

                        while (iterator.hasNext()) {
                            ProviderNode node = iterator.next();
                            //如果权重值小于等于0，进行invoker删除操作
                            if(node.getConstantWeight()<=0){
                                invokers.removeIf(invoker -> invoker.getUrl().toFullString().contains(node.getIp()));
                                iterator.remove();
                                iterator = nodes.iterator();
                            }else{//进行权重变更,修改ip地址对应的invoker的weight值
                                for (int j=0;j< invokers.size();j++) {
                                    final Invoker invokerTemp = invokers.get(j);
                                    com.alibaba.dubbo.common.URL url = invokerTemp.getUrl().addParameter(Constants.WEIGHT_KEY,node.getConstantWeight());
                                    Predicate<Invoker> filter = new Predicate<Invoker>() {
                                        @Override
                                        public boolean test(Invoker invoker) {
                                            return invokerTemp.getUrl().toFullString().contains(node.getIp());
                                        }
                                    };
                                    if(filter.test(invokerTemp)){
                                        invokers.set(j,new InvokerWrapper(invokerTemp,url));
                                        iterator.remove();
                                        iterator = nodes.iterator();
                                        break;
                                    }
                                }
                            }
                        }
                        if(0 == invokers.size()){
                            logger.error(String.format(ERROR_MSG,invocation.getMethodName(),getInterface().getName(),directory.getUrl().getAddress(),NetUtils.getLocalHost(),Version.getVersion()));
                            throw new RpcException(String.format(ERROR_MSG,invocation.getMethodName(),getInterface().getName(),directory.getUrl().getAddress(),NetUtils.getLocalHost(),Version.getVersion()));
                        }
                        //step2:读取redis中A/B test策略,按照配置规则，执行策略分发算法
                        resultTemp = redisClient.invoke("hget",String.format(com.husky.common.Constants.REDIS_KEY_PREFIX, com.husky.common.Constants.REDIS_KEY_FILL_GLOBAL),interfacePath,null);
                        List<Soil> soils = JSONArray.parseArray(resultTemp.getResult(), Soil.class);
                        //step2:当前接口路径配置有算法策略
                        if(null != soils && 0 < soils.size()){
                            int weightSum = 0;
                            List<String> keys = new ArrayList<>();
                            for (Soil soil:soils) {
                                String key = new StringBuilder(interfacePath).append(":").append(soil.getAlgName()).toString();
                                weightSum+=soil.getWeight();
                                //将redis中信息及时更新到本机内存中
                                integerWrapperMap.putIfAbsent(key,new IntegerWrapper(soil.getAlgName(),soil.getWeight(),soil.getWeight()));
                                IntegerWrapper integerWrapperTemp =  integerWrapperMap.get(key);
                                boolean isUpdate = false;
                                if(soil.getAlgName().indexOf(integerWrapperTemp.algName)<0){//策略发生变更
                                    integerWrapperTemp.setAlgName(soil.getAlgName());
                                    isUpdate = true;
                                }else if(soil.getWeight()!=integerWrapperTemp.getConstantValue()){//权重发生变更
                                    isUpdate = true;
                                }
                                if(isUpdate){
                                    integerWrapperTemp.setCurrentValue(soil.getWeight());
                                    integerWrapperTemp.setConstantValue(soil.getWeight());
                                    integerWrapperMap.put(key,integerWrapperTemp);
                                }
                                keys.add(key);
                            }
                            //分配算法策略
                            IntegerWrapper maxWeightIntegerWrapper = null;
                            for (String key:keys) {
                                IntegerWrapper integerWrapper = integerWrapperMap.get(key);
                                //the Current weight plus with fixed value
                                integerWrapper.setCurrentValue(integerWrapper.getCurrentValue()+integerWrapper.getConstantValue());
                                if(null == maxWeightIntegerWrapper || maxWeightIntegerWrapper.getCurrentValue() < integerWrapper.getCurrentValue()){
                                    maxWeightIntegerWrapper = integerWrapper;
                                }
                            }
                            maxWeightIntegerWrapper.setCurrentValue(maxWeightIntegerWrapper.getCurrentValue()-weightSum);

                            Map map = ((RpcInvocation)invocation).getAttachments();
                            String algName = maxWeightIntegerWrapper.getAlgName();
                            if(!StringUtils.isEmpty(algName)){
                                map.put("algStrategy",algName);
                            }
                            ((RpcInvocation)invocation).addAttachments(map);
                        }
                    }catch (Exception e){
                        logger.error("An exception occurred during traffic distribution,interface:{"+interfacePath+"},redis value:{"+resultTemp.getResult()+"},e:{"+e+"}");
                    }
                }

                //No provider available from registry 127.0.0.1:2181 for service com.alibaba.dubbo.demo.DemoService on consumer 10.17.53.110 use dubbo version 2.0.0,
                List<Invoker<T>> copyinvokers = invokers;
                //check invokers is valid？
                checkInvokers(copyinvokers, invocation);
                int len = getUrl().getMethodParameter(invocation.getMethodName(), Constants.RETRIES_KEY, Constants.DEFAULT_RETRIES) + 1;
                if (len <= 0) {
                    len = 1;
                }
                // retry loop.
                RpcException le = null; // last exception.
                List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyinvokers.size()); // invoked invokers.
                Set<String> providers = new HashSet<String>(len);
                for (int i = 0; i < len; i++) {
                    //Reselect before retry to avoid a change of candidate `invokers`.
                    //NOTE: if `invokers` changed, then `invoked` also lose accuracy.
                    if (i > 0) {
                        checkWhetherDestroyed();
                        copyinvokers = list(invocation);
                        // check again
                        checkInvokers(copyinvokers, invocation);
                    }
                    Invoker<T> invoker = select(loadbalance, invocation, copyinvokers, invoked);
                    invoked.add(invoker);
                    RpcContext.getContext().setInvokers((List) invoked);
                    try {
                        Result result = invoker.invoke(invocation);
                        if (le != null && logger.isWarnEnabled()) {
                            logger.warn("Although retry the method " + invocation.getMethodName()
                                    + " in the service " + getInterface().getName()
                                    + " was successful by the provider " + invoker.getUrl().getAddress()
                                    + ", but there have been failed providers " + providers
                                    + " (" + providers.size() + "/" + copyinvokers.size()
                                    + ") from the registry " + directory.getUrl().getAddress()
                                    + " on the consumer " + NetUtils.getLocalHost()
                                    + " using the dubbo version " + Version.getVersion() + ". Last error is: "
                                    + le.getMessage(), le);
                        }
                        return result;
                    } catch (RpcException e) {
                        if (e.isBiz()) { // biz exception.
                            throw e;
                        }
                        le = e;
                    } catch (Throwable e) {
                        le = new RpcException(e.getMessage(), e);
                    } finally {
                        providers.add(invoker.getUrl().getAddress());
                    }
                }
                throw new RpcException(le != null ? le.getCode() : 0, "Failed to invoke the method "
                        + invocation.getMethodName() + " in the service " + getInterface().getName()
                        + ". Tried " + len + " times of the providers " + providers
                        + " (" + providers.size() + "/" + copyinvokers.size()
                        + ") from the registry " + directory.getUrl().getAddress()
                        + " on the consumer " + NetUtils.getLocalHost() + " using the dubbo version "
                        + Version.getVersion() + ". Last error is: "
                        + (le != null ? le.getMessage() : ""), le != null && le.getCause() != null ? le.getCause() : le);
            }
        };
    }

    public static final class ProviderNode {
        private int currentWeight;//当前权重
        private int constantWeight;//固定权重
        private long timestamp;//当前时间戳
        private String ip;//节点ip

        public ProviderNode(int currentWeight, int constantWeight, String ip) {
            this.currentWeight = currentWeight;
            this.constantWeight = constantWeight;
            this.ip = ip;
        }

        public int getCurrentWeight() {
            return currentWeight;
        }

        public void setCurrentWeight(int currentWeight) {
            this.currentWeight = currentWeight;
        }

        public int getConstantWeight() {
            return constantWeight;
        }

        public void setConstantWeight(int constantWeight) {
            this.constantWeight = constantWeight;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }
    }


    public static final class IntegerWrapper {
        private String algName;
        private int currentValue;
        private int constantValue;
        private long timestamp;//当前时间戳

        public IntegerWrapper(String algName,int currentValue, int constantValue) {
            this.algName = algName;
            this.currentValue = currentValue;
            this.constantValue = constantValue;
            record();

        }

        public long getTimestamp() {
            return timestamp;
        }

        private void record(){
            this.timestamp = System.currentTimeMillis();
        }

        public String getAlgName() {
            return algName;
        }

        public void setAlgName(String algName) {
            this.algName = algName;
        }

        public int getCurrentValue() {
            return currentValue;
        }

        public void setCurrentValue(int currentValue) {
            this.currentValue = currentValue;
            record();
        }

        public int getConstantValue() {
            return constantValue;
        }

        public void setConstantValue(int constantValue) {
            this.constantValue = constantValue;
        }
    }

    private static class Relevance{
        private String interfaceName;
        private String algName;

        public String getInterfaceName() {
            return interfaceName;
        }

        public void setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
        }

        public String getAlgName() {
            return algName;
        }

        public void setAlgName(String algName) {
            this.algName = algName;
        }
    }

    private void cleanUp(){
        Iterator<Map.Entry<String,IntegerWrapper>> it = integerWrapperMap.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<String,IntegerWrapper> entry=it.next();
            if((System.currentTimeMillis()-entry.getValue().getTimestamp())>60*60*12*1000){
                it.remove();
            }
        }
    }
}
