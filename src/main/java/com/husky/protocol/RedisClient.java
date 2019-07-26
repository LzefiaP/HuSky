package com.husky.protocol;


import com.alibaba.fastjson.JSONObject;
import com.husky.Result;
import com.husky.common.Constants;
import com.husky.exception.MessageException;
import com.husky.transport.Soil;
import com.husky.utils.ConfigUtils;
import com.husky.utils.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class RedisClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    public static final int DEFAULT_PORT = 6221;
    public static final String GET = "get";
    public static final String SET = "set";
    public static final String DELETE = "delete";
    public static final String HGET = "hget";
    public static final String HGETALL = "hgetAll";
    public static final String HSET = "hset";
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }
    private static volatile RedisClient sington;

    //项目启动,立即进行加载，不然初始化连接会很慢。
    static{
        RedisClient.instance();
    }

    private JedisPool jedisPool;

    public static RedisClient instance(){
        if (sington == null) {
            synchronized (RedisClient.class) {
                if (sington == null) {
                    sington = new RedisClient();
                }
            }
        }
        return sington;
    }

    private RedisClient() {
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setTestOnBorrow(true);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(false);
        config.setMaxIdle(Integer.valueOf(ConfigUtils.getProperty("max.idle")));
        config.setMinIdle(Integer.valueOf(ConfigUtils.getProperty("min.idle")));
        config.setMaxTotal(Integer.valueOf(ConfigUtils.getProperty("max.total")));
        config.setMaxWaitMillis(Integer.valueOf(ConfigUtils.getProperty("max.wait.millis")));
        config.setNumTestsPerEvictionRun(Integer.valueOf(ConfigUtils.getProperty("num.tests.per.eviction.run")));
        config.setTimeBetweenEvictionRunsMillis(Long.valueOf(ConfigUtils.getProperty("time.between.eviction.runs.millis")));
        config.setMinEvictableIdleTimeMillis(Long.valueOf(ConfigUtils.getProperty("min.evictable.idle.time.millis")));
        jedisPool = new JedisPool(config,ConfigUtils.getProperty("redis.host"),Integer.valueOf(ConfigUtils.getProperty("redis.port")),
                Constants.DEFAULT_TIMEOUT,ConfigUtils.getProperty("redis.auth"));
        List<Jedis> minIdleJedisList = new ArrayList<Jedis>(config.getMinIdle());

        //预热 jedisPool
        for (int i = 0; i < config.getMinIdle(); i++) {
            Jedis jedis = null;
            try {
                jedis = jedisPool.getResource();
                minIdleJedisList.add(jedis);
                jedis.ping();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }finally {

            }
        }

        for (int i = 0; i < config.getMinIdle(); i++) {
            Jedis jedis = null;
            try {
                jedis = minIdleJedisList.get(i);
                jedis.close();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }finally {

            }
        }
    }

    public Result invoke(final String operate, final String key, final String field, final String value){
        Jedis resource = null;
        Result result = null;
        try {
            resource = jedisPool.getResource();
            if (GET.equals(operate)) {
                String redisResult = resource.get(key);
                if(!StringUtils.isEmpty(redisResult)){
                    result = Result.success(redisResult);
                }else{
                    result = Result.fail();
                }
            } else if (SET.equals(operate)) {
                if(!StringUtils.isEmpty(resource.set(key,value))){
                    result = Result.success();
                }else{
                    result = Result.fail();
                }
            } else if (DELETE.equals(operate)) {
                if(0<resource.del(key)){
                    result = Result.success();
                }else{
                    result = Result.fail();
                }
            }else if (HGET.equals(operate)) {//hget 命令
                String redisResult = resource.hget(key,field);
                if(!StringUtils.isEmpty(redisResult)){
                    result = Result.success(redisResult);
                }else{
                    result = Result.success(null);
                }
            }else if (HGETALL.equals(operate)) {//hgetAll 命令
                Map<String,String> redisResult = resource.hgetAll(key);
                if(null!=redisResult || redisResult.size()>0){
                    result = Result.success(redisResult);
                }else{
                    result = Result.success(null);
                }
            }else if (HSET.equals(operate)) {//hset 命令
                Long redisResult = resource.hset(key,field,value);
                if(null!=redisResult && 0<redisResult){
                    result = Result.success();
                }else{
                    result = Result.fail();
                }
            } else {
                throw new UnsupportedOperationException("Unsupported operate " + operate + " in redis service.");
            }
        } catch (Throwable t) {
            MessageException re = new MessageException("Failed to refer redis service. key: " + key + ", value: " + value + ", cause: " + t.getMessage(), t);
            if (t instanceof TimeoutException || t instanceof SocketTimeoutException) {
                re.setCode(MessageException.TIMEOUT_EXCEPTION);
            } else if (t instanceof JedisConnectionException || t instanceof IOException) {
                re.setCode(MessageException.NETWORK_EXCEPTION);
            } else if (t instanceof JedisDataException) {
                re.setCode(MessageException.SERIALIZATION_EXCEPTION);
            }
            throw re;
        } finally {
            if (resource != null) {
                resource.close();
            }
        }
        return result;
    }

    public void destroy() {
        try {
            jedisPool.destroy();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        //System.out.println(pool.invoke("set","pp","demo").toString());
        for (int i = 0; i < 500; i++) {
            long start=System.currentTimeMillis();
            List<Soil> list  = new ArrayList();
            list.add(new Soil("com.alibaba.dubbo.demo.DemoService",70,"subALGStrategy",null,null));
            list.add(new Soil("com.alibaba.dubbo.demo.DemoService",30,"subALGStrategy2",null,null));

            String requestString = JSONObject.toJSONString(list);
            System.out.println(requestString);
            String result = RedisClient.instance()
                    .invoke("hset",String.format(Constants.REDIS_KEY_PREFIX,Constants.REDIS_KEY_FILL_GLOBAL),"com.alibaba.dubbo.demo.DemoService",requestString)
                    .toString();
            long end = System.currentTimeMillis();
            System.out.println("第"+(i+1)+",result:"+result+",cost:"+(end-start)+"ms!");
        }


    }
}
