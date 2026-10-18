package com.paytech.tax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 税务服务（tax-service）唯一启动入口
 * <li>组件扫描根为 {@code com.paytech.tax}，覆盖本服务 core 与 app；其他服务只经 api 合同协作。</li>
 */
@SpringBootApplication
public class TaxApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaxApplication.class, args);
    }
}
