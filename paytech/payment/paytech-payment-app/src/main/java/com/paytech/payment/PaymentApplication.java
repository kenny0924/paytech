package com.paytech.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Payment 唯一应用入口，各业务模块共用本库与事务管理器。 */
@SpringBootApplication
public class PaymentApplication {
    /** 启动支付服务；应用不创建、迁移或初始化业务表。 */
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
