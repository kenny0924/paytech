package com.paytech.platform.support.api.referencedata.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** 新建 DRAFT 币种规则。 */
@Getter
public final class CreateCurrencySpecRequestBody {

    /**
     * ISO 4217 三位币种代码
     * <li>大写字母，创建后不可变。</li>
     */
    @NotBlank @Pattern(regexp = "[A-Z]{3}")
    private final String currency;

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
     * 操作原因
     * <li>必填，最多 500 字符；保存时去除首尾空白。</li>
     */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public CreateCurrencySpecRequestBody(@JsonProperty("currency") String currency,
        @JsonProperty("minorUnit") Integer minorUnit, @JsonProperty("roundingMode") String roundingMode,
        @JsonProperty("reason") String reason) {
        this.currency = currency;
        this.minorUnit = minorUnit;
        this.roundingMode = roundingMode;
        this.reason = reason;
    }
}
