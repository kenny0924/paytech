package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/**
 * 不可变资格决定（t_qualification_decision）
 * <li>入驻审核：每个案件最多一条 REVIEWER 决定，不填决定版本。</li>
 * <li>手工登记与撤销：MANUAL 决定，不关联案件，依据写 basis_note、原因代码写 override_reason_code。</li>
 */
@Data
public class QualificationDecision {
    /** 决定 ID。 */
    private long decisionId;

    /**
     * 案件 ID
     * <li>MANUAL 决定为空。</li>
     */
    private Long caseId;

    /** 角色类型。 */
    private String roleType;

    /**
     * 角色标识
     * <li>OPERATING_ENTITY 为 entity_no；CLIENT、LOCAL_PARTNER、RELATED_PERSON 为十进制 ID 文本。</li>
     */
    private String roleId;

    /** 资格类型。 */
    private String qualificationType;

    /** 决定来源：REVIEWER 或 MANUAL。 */
    private String decisionSource;

    /** 结论：APPROVED、REJECTED 或 REVOKED。 */
    private String outcome;

    /**
     * 覆盖或登记原因代码
     * <li>MANUAL 必填；REVIEWER 为空。</li>
     */
    private String overrideReasonCode;

    /**
     * 手工登记或撤销的依据说明
     * <li>MANUAL 必填，最多 1000 字符；其他来源为空。</li>
     */
    private String basisNote;

    /** 决定时冻结的尽调档次；MANUAL 为空。 */
    private String tier;
    /** 决定时冻结的客户风险评级；MANUAL 或未评级为空。 */
    private String riskRating;
    /** 决定时冻结的辖区代码；MANUAL 为空。 */
    private String jurisdictionCode;
    /** 决定时冻结的责任 PayFac；平台自营或 MANUAL 为空。 */
    private Long responsiblePartnerId;
    /** 推荐人；MANUAL 或未推荐为空。 */
    private Long recommendedBy;
    /** 推荐时间。 */
    private Instant recommendedTime;
    /** 程序不要求四眼时为 1，要求时为 0；MANUAL 为空。 */
    private Integer fourEyesWaived;
    /** 操作方 PLATFORM；历史行为空。 */
    private String operatedBy;
    /** 代操作的责任 PayFac；非代操作为空。 */
    private Long onBehalfOfPartnerId;
    /** 有效开始 UTC 时间。 */
    private Instant validFrom;

    /**
     * 有效结束 UTC 时间
     * <li>为空表示没有预定结束时间。</li>
     */
    private Instant validTo;

    /** 决定人 Operations 账号 ID。 */
    private Long decidedBy;

    /** 决定 UTC 时间。 */
    private Instant decidedTime;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
