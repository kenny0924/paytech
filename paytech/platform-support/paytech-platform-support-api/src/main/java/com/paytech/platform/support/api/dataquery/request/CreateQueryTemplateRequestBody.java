package com.paytech.platform.support.api.dataquery.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

/** 新建模板：保存为版本 1，状态 ACTIVE，立即可执行。 */
@Getter
@Setter
public class CreateQueryTemplateRequestBody {

    /**
     * 模板编码
     * <li>全局唯一，创建后不可修改，执行接口按它定位模板；格式 [A-Z][A-Z0-9_]{1,63}。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "templateCode 只能为大写字母开头的大写字母、数字与下划线，2..64 位")
    private String templateCode;

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

    /**
     * 联合查询配置（joint_json）
     * <li>可选；为 null 表示该版本不参与联合查询。旧客户端不传即清空，调用方须原样回传已有配置。</li>
     * <li>校验不通过返回 422，field 形如 joint.columns[1].keyType；参与情况通过 warnings 提示。</li>
     */
    @Valid
    private QueryTemplateJointConfig joint;

    /** 变更说明，可选，最多 512 字符；记录在新版本上。 */
    @Size(max = 512)
    private String changeNote;
}
