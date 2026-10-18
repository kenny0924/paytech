package com.paytech.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 对账服务（reconciliation-service）唯一启动入口
 * <li>组件扫描根为 {@code com.paytech.reconciliation}，覆盖本服务 core 与 app；其他服务只经 api 合同协作。</li>
 * <li>MyBatis 只扫描本服务 core 的 {@code com.paytech.reconciliation.core.dal.mapper}（01 §6），见 ReconciliationPersistenceConfiguration。</li>
 */
@SpringBootApplication
public class ReconciliationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationApplication.class, args);
    }
}
