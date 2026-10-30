package com.paytech.platform.support.api.dataquery.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 联合查询的键参数：键值绑定到模板的哪个参数。 */
@Getter
@Setter
public class QueryTemplateJointKeyParameter {

    /**
     * 参数名
     * <li>必须是 parameters 中已声明的参数，否则保存返回 422，field 为 joint.keyParameter.name。</li>
     * <li>参与联合查询要求该参数必填；LIST 参数一次传入本层全部键值，标量参数逐值执行。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String name;

    /**
     * 键类型编码
     * <li>须为 ACTIVE 键类型；INTEGER 键可绑定 INTEGER、STRING 或其 LIST 参数，STRING 键只能绑定 STRING 或其 LIST 参数。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "keyType 只能为大写字母开头的大写字母、数字与下划线，2..64 位")
    private String keyType;
}
