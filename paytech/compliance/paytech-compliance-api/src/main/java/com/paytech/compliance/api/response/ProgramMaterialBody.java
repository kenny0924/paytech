package com.paytech.compliance.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 程序的一个材料槽。 */
@Getter
@Setter
public class ProgramMaterialBody {

    /** 材料要求代码，例如 REGISTRATION、IDENTITY。 */
    private String requirementCode;

    /**
     * 材料归属的案件主体角色
     * <li>PRIMARY 表示受审主体本身；关联人员的材料由 RELATED_PERSON 角色程序给出。</li>
     */
    private String subjectRole;

    /** 允许提交的材料类型代码，至少一项，按代码排序。 */
    private List<String> documentTypes;

    /** 是否必需。 */
    private Boolean required;

    /**
     * 材料最长时效天数
     * <li>签发日期距提交不得超过该天数；为空不限。</li>
     */
    private Integer maxAgeDays;

    /** 审核员是否可豁免。 */
    private Boolean waivable;
}
