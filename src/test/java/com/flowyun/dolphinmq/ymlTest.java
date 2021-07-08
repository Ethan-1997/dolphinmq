package com.flowyun.dolphinmq;

import com.flowyun.dolphinmq.common.DolphinMQConfig;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 〈一句话功能简述〉
 * 〈功能详细描述〉
 *
 * @author Barry
 * @since 2021/7/8 15:47
 */
public class ymlTest {
    @Test
    void test() {
        Yaml yaml = new Yaml();
        DolphinMQConfig config = yaml.loadAs(ymlTest.class.getResourceAsStream("/dolphinmq-config.yml"),
                DolphinMQConfig.class);
        System.out.println(config.toString());

    }
}
