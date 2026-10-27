package com.paytech.compliance.api.response;

import lombok.Getter;
import lombok.Setter;

/** 程序的一个名单筛查项。 */
@Getter
@Setter
public class ProgramScreeningBody {

    /** 筛查类型：SANCTIONS、PEP 或 ADVERSE_MEDIA。 */
    private String screeningType;

    /** 被筛查的案件主体角色，PRIMARY 表示受审主体本身。 */
    private String subjectRole;

    /** 是否必需完成。 */
    private Boolean required;

    /** 批准后是否需要持续监控（本期只登记，不执行）。 */
    private Boolean monitoringRequired;
}
