package com.husky.transport;

import java.util.List;

public class Soil {
    private String interfacePath;//接口全路径
    private int weight;//权重
    private String algName;//策略名称
    private List<String> providerList;//服务列表
    private String ip;//服务节点ip

    private Soil() {
    }

    public Soil(String interfacePath, int weight, String algName, List<String> providerList,String ip) {
        this.interfacePath = interfacePath;
        this.weight = weight;
        this.algName = algName;
        this.providerList = providerList;
        this.ip = ip;
    }

    public String getInterfacePath() {
        return interfacePath;
    }

    public void setInterfacePath(String interfacePath) {
        this.interfacePath = interfacePath;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getAlgName() {
        return algName;
    }

    public void setAlgName(String algName) {
        this.algName = algName;
    }

    public List<String> getProviderList() {
        return providerList;
    }

    public void setProviderList(List<String> providerList) {
        this.providerList = providerList;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
