package com.paytech.commercial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * commercial 服务启动入口（mor_commercial，18108 / 管理 19108）。
 */
@SpringBootApplication
public class CommercialApplication {

    /**
     * 启动 commercial 服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CommercialApplication.class, args);
    }
}
