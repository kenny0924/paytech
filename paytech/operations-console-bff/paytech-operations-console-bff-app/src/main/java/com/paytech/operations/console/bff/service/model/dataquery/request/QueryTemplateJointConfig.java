package com.paytech.operations.console.bff.service.model.dataquery.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 模板版本的联合查询配置（joint_json）；与正文、参数、列声明同版本保存，不可变。
 * <li>为 null 表示该版本不参与联合查询，不做任何联合查询校验。</li>
 * <li>保存校验不通过返回 422，问题的 field 形如 joint.subjectKeyType、joint.keyParameter.name、joint.columns[1].keyType。</li>
 * <li>引用的键类型须存在且为 ACTIVE。</li>
 */
@Getter
@Setter
public class QueryTemplateJointConfig {

    /**
     * 行主体键类型编码
     * <li>结果每一行代表的业务对象；决定节点在链路图中的泳道（键类型分组）与泳道内顺序。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "subjectKeyType 只能为大写字母开头的大写字母、数字与下划线，2..64 位")
    private String subjectKeyType;

    /** 键参数：触发该模板的键类型及其绑定的参数。 */
    @NotNull
    @Valid
    private QueryTemplateJointKeyParameter keyParameter;

    /** 列标注，可选，最多 200 项；没有 LINK 列的模板是链路终点。 */
    @Size(max = 200)
    private List<@Valid @NotNull QueryTemplateJointColumn> columns;
}
