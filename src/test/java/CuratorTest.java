import com.husky.protocol.zookeeper.ChildListener;
import com.husky.protocol.zookeeper.CuratorZookeeperClient;
import com.husky.protocol.zookeeper.ZookeeperClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CuratorTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ZookeeperClient client = new CuratorZookeeperClient();

    @Test
    public void test(){
        client.addChildListener("/HuSky",new ChildListener(){
            @Override
            public void childChanged(String path, List<String> children) {
                //
                logger.error("path["+path+"]，数据发生更新,即将同步到redis!");
            }
        });

        client.create("/HuSky/flow",false,"test");


        while (true){
            synchronized (CuratorTest.this){
                try {
                    CuratorTest.this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    @Test
    public void setData() throws Exception{
        String path = "/HuSky";
        TreeCache treeCache = new TreeCache((CuratorFramework)client.getZkClient(),path);
        treeCache.getListenable().addListener((client,event)-> {
            System.out.println("-----------------------------");
            System.out.println("event:"  + event.getType());
            if (event.getData()!=null){
                System.out.println("path:" + event.getData().getPath());
                System.out.println("data:" + new String(event.getData().getData()));

            }
            System.out.println("-----------------------------");
        });
        treeCache.start();


        client.addChildListener("/HuSky",new ChildListener(){
            @Override
            public void childChanged(String path, List<String> children) {
                //
                logger.error("path["+path+"]，数据发生更新,即将同步到redis!");
            }
        });
        client.setData("/HuSky",null);

        client.setData("/HuSky/flow","test2");


        while (true){
            synchronized (CuratorTest.this){
                try {
                    CuratorTest.this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
