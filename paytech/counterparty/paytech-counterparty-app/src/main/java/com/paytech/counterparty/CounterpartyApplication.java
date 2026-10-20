package com.paytech.counterparty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * counterparty 服务启动入口。
 */
@SpringBootApplication
public class CounterpartyApplication {

    /**
     * 启动 counterparty 服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CounterpartyApplication.class, args);
    }
}
