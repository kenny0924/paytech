package com.paytech.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 数据服务启动类（D25 / D35）：Doris 上的只读数据服务。
 *
 * <p>
 * 阶段 0 只有 CDC 白名单目录与 Doris 连通性健康检查，不提供查询接口。
 *
 * @author Claude
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DataServiceApplication {

    /**
     * 启动应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DataServiceApplication.class, args);
    }
}
