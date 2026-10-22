package com.paytech.platform.support.api.dataquery.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

/** 修改模板：保存为新版本并立即生效；编码不可修改。 */
@Getter
@Setter
public class UpdateQueryTemplateRequestBody {

    /** 模板名称，1..128 字符。 */
    @NotBlank
    @Size(max = 128)
    private String templateName;

    /** 说明，可选，最多 512 字符。 */
    @Size(max = 512)
    private String description;

    /**
     * 数据源 ID
     * <li>规范正整数字符串；保存与校验时数据源必须为 ACTIVE。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "dataSourceId 必须为规范正整数字符串")
    private String dataSourceId;

    /**
     * 模板正文
     * <li>统一模板语法；UTF-8 不超过 data-query.limits.max-body-length（默认 48 KB，Gateway Body 上限 64 KB 内留出其余字段）。</li>
     */
    @NotBlank
    @Size(max = 49152)
    private String body;

    /**
     * 参数声明（parameters_json）
     * <li>JSON 数组，每项 {name,type,elementType,required,default,maxLength,min,max,allowedValues,maxSize,choices,fields}；
     * 缺省为空数组。结构不合法时问题的 field 形如 parameters[2].choices。</li>
     */
    private JsonNode parameters;

    /** 出参列声明（columns_json），可选，最多 200 项。 */
    @Size(max = 200)
    private List<@Valid @NotNull QueryTemplateColumn> columns;

    /** 约束（constraints_json）。 */
    @NotNull
    @Valid
    private QueryTemplateConstraints constraints;

    /** 变更说明，可选，最多 512 字符；记录在新版本上。 */
    @Size(max = 512)
    private String changeNote;

    /**
     * 调用方读取的模板原版本
     * <li>正整数；版本不符返回 409。</li>
     */
    @NotNull
    @Positive
    private Integer expectedVersion;
}
