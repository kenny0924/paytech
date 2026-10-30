package com.paytech.operations.console.bff.service.model.dataquery.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 启用或停用联合查询键类型。 */
@Getter
@Setter
public class ChangeQueryKeyTypeStatusRequestBody {

    /**
     * 目标状态
     * <li>ACTIVE 或 INACTIVE；停用时被 ACTIVE 模板当前版本引用返回 400，消息列出前 5 个模板编码。</li>
     */
    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE", message = "status 只能为 ACTIVE 或 INACTIVE")
    private String status;

    /**
     * 调用方读取的键类型原版本
     * <li>正整数；版本不符返回 409。</li>
     */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
