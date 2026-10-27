package com.paytech.compliance.api.response;

import lombok.Getter;
import lombok.Setter;

/** 一项检查的当前结果。 */
@Getter
@Setter
public class ClientReviewCheckResponseBody {

    /** 检查 ID。 */
    private String checkId;

    /** 检查代码。 */
    private String checkCode;

    /** 检查类型代码。 */
    private String checkType;

    /** MANUAL 或 AUTOMATED。 */
    private String checkMode;

    /** 未通过是否阻断批准。 */
    private Boolean blocking;

    /** 是否必需。 */
    private Boolean required;

    /** REQUESTED、RUNNING、COMPLETED、FAILED 或 UNKNOWN。 */
    private String status;

    /** PASS、FAIL、REVIEW 或 NOT_EXECUTED；未出结果为空。 */
    private String outcome;

    /** 结论原因码。 */
    private String reasonCode;

    /** 查询或名单版本引用。 */
    private String evidenceRef;

    /** 内部备注。 */
    private String note;

    /** 录入人。 */
    private String reviewerId;

    /** 录入时间。 */
    private String reviewedAt;

    /** 结果有效截止时间；不设有效期为空。 */
    private String validUntil;

    /** 从上一轮案件复用时的来源案件 ID；否则为空。 */
    private String reusedFromCaseId;
}
