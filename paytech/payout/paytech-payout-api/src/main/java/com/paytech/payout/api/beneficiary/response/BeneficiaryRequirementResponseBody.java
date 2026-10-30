package com.paytech.payout.api.beneficiary.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 收款方式 × 开户国家 × 币种的字段要求（P12）
 * <li>本期来自 payout 内置静态清单（Q-1）；S3 由 channel-connector 出款账户能力取代。</li>
 */
@Getter
@Setter
public class BeneficiaryRequirementResponseBody {
    /** 收款方式：LOCAL、SWIFT */
    private String railType;

    /** 开户国家 */
    private String bankCountry;

    /** 币种 */
    private String currency;

    /** 字段要求，按清单顺序 */
    private List<BeneficiaryFieldRequirement> fields;

    /** 一个字段的要求。 */
    @Getter
    @Setter
    public static class BeneficiaryFieldRequirement {
        /** 字段代码：holderName、iban、accountNumber、bankCode、swiftBic、bankName、accountType */
        private String code;

        /** 是否单独必填 */
        private boolean required;

        /**
         * 二选一分组
         * <li>同组字段至少且至多填一个（例如 SWIFT 的 iban 与 accountNumber）；无分组为空。</li>
         */
        private String oneOfGroup;

        /** 规范化后需整体匹配的正则；无格式要求为空 */
        private String pattern;

        /** 最大长度 */
        private int maxLength;

        /** 前端文案 key */
        private String labelKey;
    }
}
