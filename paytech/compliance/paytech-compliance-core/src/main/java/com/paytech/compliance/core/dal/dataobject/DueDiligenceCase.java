package com.paytech.risk.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 尽调案件（t_due_diligence_case）；Client 入驻只写 APPLICATION 来源列。 */
@Data
public class DueDiligenceCase {
    /** 案件 ID。 */
    private long caseId;

    /** 案件编号。 */
    private String caseNo;

    /** 角色类型：CLIENT 或 RELATED_PERSON。 */
    private String roleType;

    /** 角色 ID：Client ID 或关联人员 Party ID。 */
    private long roleId;

    /** 资格类型。 */
    private String qualificationType;

    /** 生效程序修订 ID。 */
    private long ddProgramVersionId;

    /** 案件来源：APPLICATION。 */
    private String caseOrigin;

    /** 来源命名空间：CCT_CLIENT_APPLICATION。 */
    private String sourceNamespace;

    /** 来源申请 ID。 */
    private Long sourceApplicationId;

    /** 来源提交编号。 */
    private String sourceSubmissionId;

    /** 案件状态。 */
    private String status;

    /** 进行中为 1，终态为空。 */
    private Integer activeSlot;

    /** 运营确认的客户可见说明；退回或决定时写入。 */
    private String publicNote;

    /** 内部审核理由，禁止展示给 Client。 */
    private String resolutionNote;

    /** 退回定位分区：BASIC、BUSINESS、PEOPLE、MATERIALS。 */
    private String returnSection;

    /** 提交 UTC 时间。 */
    private Instant submittedTime;

    /** 结束（退回、通过、拒绝）UTC 时间。 */
    private Instant decisionTime;

    /** 并发版本。 */
    private long version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
