package com.paytech.counterparty.api.client.v1.customer.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Customer 的已存卡列表。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListStoredCardsResponseBody {
    /** 有效且未过期的已存卡，按保存先后排列。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    private List<StoredCardInfo> storedCards;

    /** 对外可展示的已存卡：不含卡号或完整 BIN。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StoredCardInfo {
        /** 已存卡 Token。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        private String storedCardToken;

        /** 掩码卡号：前 6 位、6 个星号与后 4 位。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        private String maskedPan;

        /**
         * 卡组织（主品牌），供展示卡组织图标
         * <li>VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS；平台 BIN 库未识别时为 null。</li>
         * <li>按平台当前 BIN 库实时识别，只用于展示；2026-10-22 新增的可选字段，旧客户端可忽略。</li>
         */
        private String cardBrand;

        /** 有效期月份，两位。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        private String expiryMonth;

        /** 有效期年份，四位。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        private String expiryYear;

        /** 保存时间。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        private Instant createdAt;
    }
}
