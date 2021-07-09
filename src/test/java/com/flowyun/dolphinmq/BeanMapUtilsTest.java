package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.utils.BeanMapUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * BeanMapUtils 测试
 *
 * @author Barry
 * @since 2021/6/29 17:12
 */
@Slf4j
public class BeanMapUtilsTest {

    @Test
    void test() {
        Testbean testbean = new Testbean("test1", 12);
        try {
            Map<String, Object> map = BeanMapUtils.toMap(testbean);
            Map<Object, Object> omap = BeanMapUtils.getObjectObjectMap(map);
            Testbean o = (Testbean) BeanMapUtils.toBean(Testbean.class, omap);
            int age = o.getAge();
            log.info("age:" + age);
        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException | InstantiationException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }


}
