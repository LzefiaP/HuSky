package com.husky.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * 流量分发抽象类
 */
public abstract class AbstractFlowDispatch implements FlowDispatch {
    protected static volatile List<ProviderFLowConfig> nodeList = new ArrayList<>() ; //配置路由和权重信息
    public AbstractFlowDispatch(ProviderFLowConfig[] nodes) {
        synchronized (AbstractFlowDispatch.class){
            for (ProviderFLowConfig node : nodes) {
                nodeList.add(node) ;
            }
        }
    }
    public ProviderFLowConfig select() {
        if (nodeList == null || nodeList.size() == 0)
            return null;
        if (nodeList.size() == 1)
            return nodeList.get(0);
        return doSelect();
    }

    protected abstract ProviderFLowConfig doSelect();
}
