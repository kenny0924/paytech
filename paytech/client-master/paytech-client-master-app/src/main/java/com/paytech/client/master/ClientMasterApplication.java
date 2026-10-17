package com.paytech.client.master;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Client & Contract 服务启动入口。
 */
@SpringBootApplication
public class ClientMasterApplication {

    /**
     * 启动 Client & Contract 服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ClientMasterApplication.class, args);
    }
}
