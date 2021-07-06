package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.consumer.SubscriptionData;
import org.jboss.marshalling.Pair;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 〈一句话功能简述〉
 * 〈功能详细描述〉
 *
 * @author Barry
 * @since 2021/7/6 11:56
 */
public class CommenTest {
    @Test
    void test() {
        SubscriptionData<Testbean> subscriptionData = new SubscriptionData<>("t1", null, Testbean.class);
        subscriptionData.setDto(new Testbean("123", 123));
        Testbean dto = subscriptionData.getDto();
        Field[] fields = dto.getClass().getDeclaredFields();
        for (Field field :
                fields) {
            String[] str = field.getType().getName().split("\\.");
            System.out.println(String.format("属性：%s %s", str[str.length - 1], field.getName()));
        }
    }
}
