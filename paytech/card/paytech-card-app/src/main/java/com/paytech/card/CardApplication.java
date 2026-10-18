package com.paytech.card;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** card-service 唯一应用入口：卡、凭证、交易工具与已存卡的受控存储，一次性读卡票据与支付成功后的存卡（design/05）。 */
@SpringBootApplication
public class CardApplication {
    /** 启动卡服务；应用不创建、迁移或初始化业务表。 */
    public static void main(String[] args) {
        SpringApplication.run(CardApplication.class, args);
    }
}
