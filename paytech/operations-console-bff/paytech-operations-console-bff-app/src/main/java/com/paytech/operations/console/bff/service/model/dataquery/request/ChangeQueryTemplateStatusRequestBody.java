package com.paytech.operations.console.bff.service.model.dataquery.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 启用或停用模板；不生成新版本。 */
@Getter
@Setter
public class ChangeQueryTemplateStatusRequestBody {

    /**
     * 目标状态
     * <li>ACTIVE 或 INACTIVE；启用时当前版本的数据源必须为 ACTIVE。</li>
     */
    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE", message = "status 只能为 ACTIVE 或 INACTIVE")
    private String status;

    /**
     * 调用方读取的模板原版本
     * <li>正整数；版本不符返回 409。</li>
     */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
