package com.husky.common;

import java.util.regex.Pattern;

public class Constants {
    public static final int DEFAULT_TIMEOUT = 1000;
    public static final String DEFAULT_HUSKY_PROPERTIES = "husky.properties";
    public static final Pattern COMMA_SPLIT_PATTERN = Pattern
            .compile("\\s*[,]+\\s*");
    public static final String REMOVE_VALUE_PREFIX = "-";
    public static final String DEFAULT_KEY = "default";
    public static final String REDIS_KEY_PREFIX = "HuSky:%s";//redis key 前缀
    public static final String REDIS_KEY_FILL_GLOBAL = "global";//redis 全局匹配填充字符
    public static final String REDIS_KEY_FILL_PROVIDER = "provider";//redis provider填充字符
    public static final String REDIS_FLOW_KEY = "HuSky:flow";//redis流量分配key




}
