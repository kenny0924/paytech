package com.paytech.client.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Client & Contract 服务启动入口。
 */
@SpringBootApplication
public class ClientContractApplication {

    /**
     * 启动 Client & Contract 服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ClientContractApplication.class, args);
    }
}
