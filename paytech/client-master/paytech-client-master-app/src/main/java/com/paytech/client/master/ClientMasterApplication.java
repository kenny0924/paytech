package com.paytech.client.master;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * client-master 服务启动入口（mor_client_master，18101 / 管理 19101）。
 */
@SpringBootApplication
public class ClientMasterApplication {

    /**
     * 启动 client-master 服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ClientMasterApplication.class, args);
    }
}
