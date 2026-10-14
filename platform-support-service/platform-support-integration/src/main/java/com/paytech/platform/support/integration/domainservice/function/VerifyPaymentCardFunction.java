package com.paytech.platform.support.integration.domainservice.function;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;
import com.paytech.platform.support.integration.domain.execution.VerifyAccountMaterial;

/**
 * 沙箱验证运行中的 PAYMENT_CARD（PayerMax 卡直付对接技术方案第 3.4 节）：测试卡来自验证账号凭据中 testCard. 前缀的五项， 不调用 Payment，忽略 Token
 * 与支付尝试参数；返回结构与正式读取相同。验证账号只能录入渠道公布的测试卡。
 */
@Component
public class VerifyPaymentCardFunction {

    /** 验证账号凭据中测试卡的名称前缀。 */
    public static final String PREFIX = "testCard.";

    /**
     * 以验证账号测试卡代替 Payment 读卡。
     *
     * @param account
     *            验证账号材料
     * @return 写入 resources.&lt;id&gt; 的结果
     */
    public Map<String, Object> apply(VerifyAccountMaterial account) {
        Map<String, String> credentials = account.getCredentials();
        if (!credentials.containsKey(PREFIX + "pan")) {
            throw new ExecutionErrorException("验证账号没有测试卡");
        }
        return PaymentCardFunction.values(credentials.get(PREFIX + "pan"), credentials.get(PREFIX + "expiryMonth"),
            credentials.get(PREFIX + "expiryYear"), credentials.get(PREFIX + "cardholderName"),
            credentials.get(PREFIX + "cvv"));
    }
}
