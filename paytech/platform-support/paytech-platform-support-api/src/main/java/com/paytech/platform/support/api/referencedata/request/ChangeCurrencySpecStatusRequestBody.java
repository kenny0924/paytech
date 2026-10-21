package com.paytech.platform.support.api.referencedata.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** 切换币种规则状态；DRAFT 只能启用，不能直接停用。 */
@Getter
public final class ChangeCurrencySpecStatusRequestBody {

    /** 目标状态：ACTIVE 或 INACTIVE。 */
    @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE")
    private final String status;

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
    public ChangeCurrencySpecStatusRequestBody(@JsonProperty("status") String status,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.status = status;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }
}
