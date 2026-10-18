package com.paytech.card.api.instrument.response;

import lombok.Getter;
import lombok.Setter;

/** 创建卡交易工具响应：不含卡号、CVV、DEK 或 Vault Token */
@Getter
@Setter
public class CreateCardInstrumentResponseBody {
    /** Card 工具引用（交易工具 ID 的十进制字符串），Payment 保存为支付工具 Token 的 instrument_ref */
    private String instrumentRef;

    /** 掩码卡号：前 6 位、6 个星号与后 4 位 */
    private String maskedPan;

    /** 工具实际期限，ISO-8601 UTC；不晚于请求的期限上限与卡有效期 */
    private String expiresAt;

    /** 支付风控所需的卡属性（design/05 §2.4），Payment 存入支付工具 Token 行，不再单独读取 */
    private RiskAttributes riskAttributes;

    /** 风控卡属性：受限值，只经内部同步请求传递，不进日志或 MQ */
    @Getter
    @Setter
    public static class RiskAttributes {
        /** 卡 BIN，6 或 8 位数字 */
        private String bin;

        /** 跨交易稳定的卡指纹，64 位小写十六进制，不能反推卡号 */
        private String fingerprint;

        @Override
        public String toString() {
            return "RiskAttributes[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "CreateCardInstrumentResponseBody[" + instrumentRef + "]";
    }
}
