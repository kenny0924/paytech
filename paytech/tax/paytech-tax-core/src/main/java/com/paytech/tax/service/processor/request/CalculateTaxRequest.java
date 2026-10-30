package com.paytech.tax.service.processor.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxPriceMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计算税务的内部请求：由 HTTP Converter 从 CalculateTaxRequestBody 转换，枚举与金额已是类型化值
 * <li>计算身份：sourceSystem + sourceObjectType + sourceObjectId + sourceRevision + taxScopeKey。</li>
 */
@Getter
@AllArgsConstructor
public class CalculateTaxRequest {
    /** 来源系统 */
    @NotBlank
    private final String sourceSystem;

    /** 来源对象类型：ORDER */
    @NotBlank
    private final String sourceObjectType;

    /** 来源对象 ID */
    @Positive
    private final long sourceObjectId;

    /** 来源对象公开编号 */
    @NotBlank
    private final String sourceObjectNo;

    /** 来源准备版本 */
    @Positive
    private final int sourceRevision;

    /** 税务范围：SALE */
    @NotBlank
    private final String taxScopeKey;

    /** 计算阶段 */
    @NotNull
    private final TaxCalculationStage stage;

    /** 供应类型：SALE */
    @NotBlank
    private final String supplyKind;

    /** 商业模式代码 */
    @NotBlank
    private final String businessMode;

    /** 卖方 Party ID；可空 */
    private final Long sellerPartyId;

    /** 税务责任方 Party ID；调用方未确定责任方时为空（结果 NOT_MANAGED） */
    private final Long taxSubjectPartyId;

    /** 买方 Client ID */
    @Positive
    private final long buyerClientId;

    /** 收单 OE 编号 */
    @NotBlank
    private final String entityNo;

    /** 币种 */
    @NotBlank
    private final String currency;

    /** 价格口径 */
    @NotNull
    private final TaxPriceMode priceMode;

    /** 候选税点日期 */
    @NotNull
    private final LocalDate taxPointDate;

    /** 国家证据；同类型至多一条 */
    @NotNull
    @Valid
    private final List<Evidence> evidence;

    /** 商品行 */
    @NotEmpty
    @Valid
    private final List<Line> lines;

    /** 自报税额；只在 NOT_MANAGED 时使用，可空 */
    private final BigDecimal declaredTaxAmount;

    /**
     * 指定类型证据的国家
     *
     * @param type 证据类型
     * @return 国家代码；没有该类型证据返回 null
     */
    public String countryOf(TaxEvidenceType type) {
        return evidence.stream().filter(item -> item.getType() == type).map(Evidence::getCountryCode).findFirst()
            .orElse(null);
    }

    /** 一条国家证据。 */
    @Getter
    @AllArgsConstructor
    public static class Evidence {
        /** 证据类型 */
        @NotNull
        private final TaxEvidenceType type;

        /** 国家代码 */
        @NotBlank
        private final String countryCode;

        /** 行政区代码；可空 */
        private final String subdivisionCode;
    }

    /** 一条商品行。 */
    @Getter
    @AllArgsConstructor
    public static class Line {
        /** 源商品行引用 */
        @NotBlank
        private final String lineRef;

        /** 行号 */
        @Positive
        private final int lineNo;

        /** 税类代码 */
        @NotBlank
        private final String categoryCode;

        /** 非敏感商品说明；可空 */
        private final String description;

        /** 数量 */
        @NotNull
        private final BigDecimal quantity;

        /** 单价 */
        @NotNull
        private final BigDecimal unitPrice;

        /** 整行折扣 */
        @NotNull
        private final BigDecimal discountAmount;

        /** 行金额 = 数量 × 单价 − 折扣 */
        @NotNull
        private final BigDecimal lineAmount;
    }
}
