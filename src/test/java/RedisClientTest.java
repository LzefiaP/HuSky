import com.alibaba.fastjson.JSONObject;
import com.husky.Result;
import com.husky.protocol.RedisClient;
import org.junit.Test;

import java.util.Map;

public class RedisClientTest {
    @Test
    public void hset(){
        String jsonString =
                "{\n" +
                        "    \"default\": {\n" +
                        "      \"type\": \"deepfm_classifier\",\n" +
                        "      \"name\": \"game_topic_deepfm_v4\",\n" +
                        "      \"port\": 80,\n" +
                        "      \"singature_name\": \"serving_default\",\n" +
                        "      \"feature\": \"game_topic_deepfm_v4.yaml\",\n" +
                        "      \"input\": \"examples\",\n" +
                        "      \"output\": \"prob\",\n" +
                        "      \"model_name\": \"deepfm_classifier_list\"\n" +
                        "    },\n" +
                        "    \"4,5\": {\n" +
                        "      \"type\": \"widedeep_classifier\",\n" +
                        "      \"name\": \"game_topic_widedeep_sync\",\n" +
                        "      \"port\": 80,\n" +
                        "      \"singature_name\": \"serving_default\",\n" +
                        "      \"feature\": \"game_topic_widedeep_sync.yaml\",\n" +
                        "      \"input\": \"inputs\",\n" +
                        "      \"output\": \"scores\",\n" +
                        "      \"model_name\": \"game_topic_widedeep_sync\"\n" +
                        "    }";
        Result result = RedisClient.instance().invoke("hset","HuSky","game_rank_topic",jsonString);
        System.out.println(result.toString());
    }

    @Test
    public void hgetAll(){
        Result<Map<String,String>> result = RedisClient.instance().invoke("hgetAll","HuSky","game_rank_topic",null);
        Map<String,String> map =  result.getResult();
        System.out.println("json:"+ JSONObject.toJSONString(map));
        /*
        for (Map.Entry<String,String> entry:map.entrySet()){
            System.out.println("key:"+entry.getKey()+",value:"+entry.getValue());
        }
        */
        //System.out.println(result.toString());


    }
}
