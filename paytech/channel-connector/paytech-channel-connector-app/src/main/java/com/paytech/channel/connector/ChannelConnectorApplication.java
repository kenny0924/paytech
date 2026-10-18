package com.paytech.channel.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** channel-connector 唯一应用入口：渠道、账号、MID 配置与经 Platform 的渠道操作执行（D06）。 */
@SpringBootApplication
public class ChannelConnectorApplication {
    /** 启动渠道连接服务；应用不创建、迁移或初始化业务表。 */
    public static void main(String[] args) {
        SpringApplication.run(ChannelConnectorApplication.class, args);
    }
}
