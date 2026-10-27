package com.paytech.compliance.api.response;

import lombok.Getter;
import lombok.Setter;

/** 程序的一个检查项。 */
@Getter
@Setter
public class ProgramCheckBody {

    /** 检查代码，例如 WEBSITE_REVIEW、MANUAL_SCREENING。 */
    private String checkCode;

    /** 检查类型代码（受控目录）。 */
    private String checkType;

    /** 执行方式：MANUAL 或 AUTOMATED；本期只有 MANUAL。 */
    private String checkMode;

    /** 检查对象的案件主体角色，PRIMARY 表示受审主体本身。 */
    private String subjectRole;

    /** 未通过（FAIL）是否阻断批准。 */
    private Boolean blocking;

    /** 结果有效天数；为空表示结果不设有效期，重提时不复用。 */
    private Integer validityDays;
}
