package com.paytech.platform.support.api.referencedata.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** 修改 DRAFT 币种规则；币种代码与状态不变。 */
@Getter
public final class UpdateCurrencySpecRequestBody {

    /**
     * 最小货币单位位数
     * <li>0..8。</li>
     */
    @NotNull @Min(0) @Max(8)
    private final Integer minorUnit;

    /**
     * 舍入方式
     * <li>{@code CurrencyRoundingMode} Code，当前只有 HALF_UP。</li>
     */
    @NotBlank @Pattern(regexp = "HALF_UP")
    private final String roundingMode;

    /**
     * 调用方读取的原版本
     * <li>必须为正整数；版本不符返回并发冲突。</li>
     */
    @Positive
    private final int expectedVersion;

    /**
     * 操作原因
     * <li>必填，最多 500 字符；保存时去除首尾空白。</li>
     */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public UpdateCurrencySpecRequestBody(@JsonProperty("minorUnit") Integer minorUnit,
        @JsonProperty("roundingMode") String roundingMode,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.minorUnit = minorUnit;
        this.roundingMode = roundingMode;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }
}
