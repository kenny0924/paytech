package com.paytech.operations.console.bff.service.model.dataquery.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 整体排序中的一项：键类型与调用方读取的原版本。 */
@Getter
@Setter
public class QueryKeyTypeOrderItem {

    /** 键类型 ID，规范正整数字符串。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "keyTypeId 必须为规范正整数字符串")
    private String keyTypeId;

    /**
     * 调用方读取的键类型原版本
     * <li>正整数；任一项版本不符整体返回 409。</li>
     */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
