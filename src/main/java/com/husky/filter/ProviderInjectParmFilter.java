package com.husky.filter;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.husky.pilot.PilotStrategy;
import com.husky.utils.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * dubbo provider 根据配置项动态织入算法策略实现类
 */
@Activate(group = {Constants.PROVIDER},order = -20000)
public class ProviderInjectParmFilter implements Filter {
    private final static Logger LOGGER = LoggerFactory.getLogger(ProviderInjectParmFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        Result result = null;
        Long takeTime = 0L;
        Long startTime = System.currentTimeMillis();
        try{
            Class[] paramTypes = invocation.getParameterTypes();

            Map<String,String> map = ((RpcInvocation)invocation).getAttachments();
            for (Map.Entry<String,String> entry:map.entrySet()) {
                LOGGER.debug(">>>>>>>>>>>>>>>key:"+entry.getKey()+",value:"+entry.getValue());
            }

            String fullPath = null;
            for (int i = 0; i < paramTypes.length; i++) {
                //1.读取配置的接口名称(全路径)
                if((PilotStrategy.class.getName()).equals(paramTypes[i].getName())){
                    fullPath = PilotStrategy.class.getName();
                    break;
                }

            }
            /*
            if(null!=fullPath && !("").equals(fullPath) && fullPath.length()>0){
                fullPath = fullPath.substring(fullPath.indexOf('.'),fullPath.length());
                char[]chars = fullPath.toCharArray();
                chars[0]+=32;
                fullPath = String.valueOf(chars);
            }
            */
            Class clazz = getClass().getClassLoader().loadClass(fullPath);
            //2.获取所有配置算法策略
            Map<String,PilotStrategy> strategyMap = SpringContextUtils.getBeansOfType(clazz);
            Object[] values = invocation.getArguments();
            for (int i = 0; i < values.length; i++) {
                if(values[i] instanceof PilotStrategy || values[i] == null){
                    //mock 读取算法策略配置
                    values[i] = strategyMap.get("subALGStrategy");
                    break;
                }
            }

        }catch(Exception e){
            LOGGER.error("fill strategy exception:",e);
        }
        try{
            result = invoker.invoke(invocation);
            if (null!=result && result.getException() instanceof Exception){
                throw new Exception(result.getException());
            }
            takeTime = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            LOGGER.error("exception:",e);
        } finally {
            LOGGER.error(">>>>>>>>>>>>>>>>>>>>>attachments:"+JSONObject.toJSONString(invocation.getAttachments()));
            LOGGER.error("interface:["+invoker.getInterface().getName()+"],method:["+invocation.getMethodName().toString()+"],request:["+ JSONObject.toJSONString(invocation.getArguments())+"],response:["+JSON.toJSON(result)+"],takeTime:["+takeTime+"] ms!");
        }
        return invoker.invoke(invocation);
    }
}
