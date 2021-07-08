<p align="center">
    <img width="400" src="https://raw.githubusercontent.com/SoulBiuBiuBiu/assets/master/images/dolphinmqlogo.png">
</p>


## ✨ Features

- 🌈 支持ACK机制
- 📦 支持异步通信
- 🛡 支持消费端线性扩展
- 🎨 支持消费者故障后由其他消费者认领
- 🌍 接口幂等性实现
## 🖥 Environment Required
- redis v5.0.0+
## ☀️ Quick Start
### Producer
```java 
Config config = new Config();
config.useSingleServer().setAddress("redis://127.0.0.1:6379");
RedissonClient redisson = Redisson.create(config);

Producer producer = new Producer(redisson);
Message msg = new Message();
Testbean test = new Testbean("test", 13);
msg.setTopic("t1");
try {
    msg.setProperties(BeanMapUtils.toMap(test));
} catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
    e.printStackTrace();
}
producer.sendMessageAsync(msg);
```
### Consumer
```java 
Config config = new Config();
config.useSingleServer().setAddress("redis://127.0.0.1:6379");
redisson = Redisson.create(config);

HiListener<Testbean> hiListener = new HiListener<>();

PullConsumerClient.builde()
        .setRedissonClient(redisson)
        .setService("service")
        .<Testbean>subscribe("t1")
        .registerListener(hiListener)
        .registerListener(hiListener)
        .<Testbean>subscribe("t2")
        .registerListener(hiListener)
        .start();
```
## 🎈 Configuration
### 配置文件
```
dolphinmq-config.yml
```
### 配置项
```
# 每次拉取数据的量
fetchMessageSize: 5
#检查consumer不活跃的门槛（单位秒）
pendingListIdleThreshold: 10
#每次拉取PendingList的大小
checkPendingListSize: 1000
#死信门槛（计次器次数）
deadLetterThreshold: 32
#认领门槛(单位毫秒)
claimThreshold: 3600
#是否从头开始订阅消息
isStartFromHead: "true"
#拉取信息的周期(单位秒)
pullHealthyMessagesPeriod: 1
#检查PendingList周期(单位秒)
checkPendingListsPeriod: 10
```


