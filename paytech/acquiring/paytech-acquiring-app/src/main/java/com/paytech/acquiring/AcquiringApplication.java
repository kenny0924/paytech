package com.paytech.commerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Commerce 服务启动入口。
 */
@SpringBootApplication
public class CommerceApplication {

    /**
     * 启动 Commerce 服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CommerceApplication.class, args);
    }
}
