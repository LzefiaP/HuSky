package com.husky.flow;

/**
 * 流量分发接口
 */
public interface FlowDispatch {
    ProviderFLowConfig select();
    /**
     * 服务提供方流量配置模块
     */
    public static class ProviderFLowConfig {
        private final int weight ;  // 初始权重 （保持不变）
        private String serverName; //机器名称
        private String interfacePath; //接口路径
        private String ip;//ip地址
        private final String algName;//具体策略名称

        private int currentWeight ; //当前权重

        public ProviderFLowConfig( String algName,int weight, String serverName) {
            this.weight = weight;
            this.algName = algName;
            this.currentWeight = weight;
            this.serverName = serverName;
        }

        public ProviderFLowConfig( String algName, int weight) {
            this(algName,weight,null);
        }

        public ProviderFLowConfig( String algName) {
            this(algName,0,null);
        }

        public int getCurrentWeight() {
            return currentWeight;
        }

        public int getWeight() {
            return weight;
        }

        public void setCurrentWeight(int currentWeight) {
            this.currentWeight = currentWeight;
        }

        public String getServerName() {
            return serverName;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }

        public String getInterfacePath() {
            return interfacePath;
        }

        public void setInterfacePath(String interfacePath) {
            this.interfacePath = interfacePath;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getAlgName() {
            return algName;
        }
    }
}
