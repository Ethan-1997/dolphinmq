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
```java 
Config config = new Config();
config.useSingleServer().setAddress("redis://127.0.0.1:6379");
RedissonClient redisson = Redisson.create(config);

PullConsumerClient pullConsumerClient = new PullConsumerClient(
        redisson,
        "consumerGroupName"
);
SubscriptionData<Testbean> t1 = pullConsumerClient.subscribe("topicName", Testbean.class);

HiListener hiListener = new HiListener();

t1.registerMessageListener(hiListener);
t1.registerMessageListener(new TopicListener<Testbean>() {
    @Override
    public void consume(Testbean dto) {
        log.info("dto:{}", dto);
    }
});

pullConsumerClient.start();
```


