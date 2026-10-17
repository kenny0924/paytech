package com.paytech.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 账户服务（account-service）唯一启动入口
 * <li>组件扫描根为 {@code com.paytech.account}，覆盖本服务 core 与 app；其他服务只经 api 合同协作。</li>
 * <li>MyBatis 只扫描本服务 core 的 {@code com.paytech.account.core.dal.mapper}（01 §6），见 AccountPersistenceConfiguration。</li>
 */
@SpringBootApplication
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
