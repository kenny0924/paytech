package com.paytech.payout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 结算出款服务（payout-service）唯一启动入口
 * <li>组件扫描根为 {@code com.paytech.payout}，覆盖本服务 core 与 app；其他服务只经 api 合同协作。</li>
 * <li>MyBatis 只扫描本服务 core 的 {@code com.paytech.payout.core.dal.mapper}，见 PayoutPersistenceConfiguration。</li>
 */
@SpringBootApplication
public class PayoutApplication {

    /** 启动 payout-service。 */
    public static void main(String[] args) {
        SpringApplication.run(PayoutApplication.class, args);
    }
}
