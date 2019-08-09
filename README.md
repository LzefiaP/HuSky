# HuSky

HuSky 是基于dubbo,spring的流量分发插件.

## 特性介绍

* 支持基于独立provider和provider list 两个维度的流量分发。
* 支持A/B Test。
* 目前支持轮询加权,一般轮询两种流量分配算法。

## 特别注意

* 本插件不打包依赖，需要自行添加maven依赖。
* 本插件基于 dubbbo 2.6.0，spring 4.3.12 开发，其他版本可能存在兼容性，类库变更等问题，需要特别注意。
* 本插件依赖redis进行数据读取。

## 集成步骤

* resource 目录下创建husky.properties 里面添加 redis 配置。
* 建议添加 jedis 2.9.0，commons-pool2 2.6.2,dubbo 2.6.0,spring 4.3.12,slf4j-api 1.7.26,log4j 1.2.16 等maven依赖。


## 写在最后

* 欢迎优秀有想法的coder,一起完善！！！
* 联系方式：pengpan1@sina.com
