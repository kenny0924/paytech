package com.paytech.finance.accounting.service.processor.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建渠道成本版本；组成的类型、计算基础与舍入方式保持 Wire 字符串，由 Processor 按目录解析。 */
@Getter
@Setter
public class CreateChannelCostVersionRequest {
    /** 方案代码 */
    @NotBlank
    private String planCode;

    /** 编制人 */
    @NotNull
    private Long makerId;

    /** 开始参与匹配的时间 */
    @NotNull
    private Instant effectiveFrom;

    /** 完整适用范围集 */
    @NotNull
    private List<@Valid Scope> scopes;

    /** 适用范围。 */
    @Getter
    @Setter
    public static class Scope {
        /** 版本内范围序号。 */
        @NotNull
        private Integer scopeNo;

        /** Payment Channel MID 稳定业务 ID。 */
        @NotNull
        private Long midId;

        /** CCT Operating Entity 编号。 */
        @NotBlank
        private String entityNo;

        /** Channel 合同或费率文件稳定引用。 */
        @NotBlank
        private String channelAgreementRef;

        /** ISO 3166-1 alpha-2 国家代码。 */
        @NotBlank
        private String countryCode;

        /** ISO 4217 币种代码。 */
        @NotBlank
        private String currency;

        /** Payment 支付方式目录代码。 */
        @NotBlank
        private String paymentMethodCode;

        /** Payment 动作类别。 */
        @NotBlank
        private String actionType;

        /** 范围内完整成本组成集。 */
        @NotNull
        private List<@Valid Component> components;

        /** 六个匹配维度组成的版本内语义唯一键。 */
        public String matchKey() {
            return String.join("|", String.valueOf(midId), entityNo, countryCode, currency, paymentMethodCode,
                actionType);
        }
    }

    /** 成本组成。 */
    @Getter
    @Setter
    public static class Component {
        /** 范围内组成代码。 */
        @NotBlank
        private String componentCode;

        /** 收费语义代码。 */
        @NotBlank
        private String componentType;

        /** 金额计算基础代码。 */
        @NotBlank
        private String calculationBase;

        /**
         * 十进制费率
         * <li>可为空；与固定费至少一项非空。</li>
         */
        private BigDecimal rateValue;

        /**
         * 固定费
         * <li>可为空，与 currency 成对。</li>
         */
        private BigDecimal fixedAmount;

        /**
         * 固定费币种
         * <li>可为空，与 fixedAmount 成对。</li>
         */
        private String currency;

        /**
         * 计算结果下限
         * <li>可为空。</li>
         */
        private BigDecimal minAmount;

        /**
         * 计算结果上限
         * <li>可为空。</li>
         */
        private BigDecimal maxAmount;

        /** 舍入方式代码。 */
        @NotBlank
        private String roundingMode;
    }
}
