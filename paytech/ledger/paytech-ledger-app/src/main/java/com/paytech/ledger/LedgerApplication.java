package com.paytech.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 会计服务（ledger-service）唯一启动入口
 * <li>组件扫描根为 {@code com.paytech.ledger}，覆盖本服务 core 与 app；其他服务只经 api 合同协作。</li>
 * <li>MyBatis 只扫描本服务 core 的 {@code com.paytech.ledger.core.dal.mapper}（01 §6），见 LedgerPersistenceConfiguration。</li>
 */
@SpringBootApplication
public class LedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
