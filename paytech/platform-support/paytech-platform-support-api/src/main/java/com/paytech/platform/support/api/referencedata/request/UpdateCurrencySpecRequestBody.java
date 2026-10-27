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

/**
 * 修改币种规则；币种代码与状态不变。
 * <li>DRAFT 可修改全部字段；非 DRAFT 时数字代码、名称、符号、小数位与舍入方式冻结，只允许修改 logo 文件地址。</li>
 */
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
     * ISO 4217 三位数字代码
     * <li>必填，三位数字，保留前导零，例如 840。</li>
     */
    @NotBlank @Pattern(regexp = "[0-9]{3}")
    private final String numericCode;

    /**
     * 英文名称
     * <li>必填，最多 128 字符；保存时去除首尾空白。</li>
     */
    @NotBlank @Size(max = 128)
    private final String name;

    /**
     * 货币符号
     * <li>必填，最多 8 字符；没有专用符号时填币种代码。</li>
     */
    @NotBlank @Size(max = 8)
    private final String symbol;

    /**
     * logo 文件地址
     * <li>可空；须为文件服务地址 {@code /files/v1/{fileId}}（格式同 {@code FileAddress}），且对应文件为公开文件。</li>
     */
    @Pattern(regexp = "^/files/v1/[1-9][0-9]{0,18}$")
    private final String logoFileAddress;

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
        @JsonProperty("reason") String reason,
        @JsonProperty("numericCode") String numericCode, @JsonProperty("name") String name,
        @JsonProperty("symbol") String symbol, @JsonProperty("logoFileAddress") String logoFileAddress) {
        this.minorUnit = minorUnit;
        this.roundingMode = roundingMode;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
        this.numericCode = numericCode;
        this.name = name;
        this.symbol = symbol;
        this.logoFileAddress = logoFileAddress;
    }
}
