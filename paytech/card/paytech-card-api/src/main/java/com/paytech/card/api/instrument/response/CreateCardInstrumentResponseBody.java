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

    /**
     * BIN 库卡属性快照（2026-10-22 BIN 库需求）
     * <li>创建（含原提交重放）时按当前生效 BIN 库查询一次；Payment 只在首次绑定支付工具 Token 时冻结，之后不随 BIN 库更新改变。</li>
     * <li>对象恒在；未识别时各属性为 null。</li>
     */
    private CardAttributes cardAttributes;

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

    /** BIN 库卡属性快照：只含 BIN 库标准化属性，不含卡号或 BIN */
    @Getter
    @Setter
    public static class CardAttributes {
        /** 是否命中 BIN 库号段；false 时各属性为 null */
        private boolean recognized;

        /**
         * 查询时的生效 BIN 库版本号
         * <li>没有生效版本时为 null；有版本但未命中时有值。</li>
         */
        private Integer binLibraryVersionNo;

        /** 命中号段的前缀位数：6、7 或 8；未命中为 null */
        private Integer matchedPrefixLength;

        /** 卡组织（主品牌），{@code CardBrand} Code：VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS；未识别为 null */
        private String cardBrand;

        /** 借贷记类型，{@code CardFundingType} Code：CREDIT、DEBIT、PREPAID、CHARGE；未识别或号段未提供为 null */
        private String fundingType;

        /** 卡等级，开放代码 [A-Z0-9_]{1,32}；未识别或号段未提供为 null */
        private String cardLevel;

        /** 卡产品类别，{@code CardProduct} Code：CONSUMER、COMMERCIAL；未识别或号段未提供为 null */
        private String cardProduct;

        /** 发卡国 ISO 3166-1 alpha-2；未识别为 null */
        private String issuerCountry;

        /** 发卡区域（导入时由 Platform 国家区域映射推导）：GCC、MEA、EUROPE、NORTH_AMERICA、LATAM、APAC；未识别为 null */
        private String issuerRegion;
    }

    @Override
    public String toString() {
        return "CreateCardInstrumentResponseBody[" + instrumentRef + "]";
    }
}
