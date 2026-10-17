package com.paytech.risk.compliance.domainservice.policy;

import java.util.ArrayList;
import java.util.List;

import com.paytech.risk.compliance.domain.ClientReviewFamily;
import com.paytech.risk.compliance.domain.ReviewRequirementView;

/**
 * Client 入驻审核的通过门禁；材料是否充分由运营逐份核验，系统只检查核验与必需项是否完成。
 * <li>无副作用，不访问数据库或外部服务。</li>
 */
public final class ClientReviewApprovalPolicy {
    /** 主案件已结束。 */
    public static final String CASE_NOT_ACTIVE = "CASE_NOT_ACTIVE";
    /** 子案件不在进行中。 */
    public static final String CHILD_CASE_NOT_ACTIVE = "CHILD_CASE_NOT_ACTIVE";
    /** 仍有未核验的材料。 */
    public static final String EVIDENCE_PENDING = "EVIDENCE_PENDING";
    /** 基础必需材料没有被接受的证据。 */
    public static final String REQUIRED_MATERIAL_NOT_ACCEPTED = "REQUIRED_MATERIAL_NOT_ACCEPTED";
    /** Risk 尚未收到已确认分类。 */
    public static final String CLASSIFICATION_NOT_RECEIVED = "CLASSIFICATION_NOT_RECEIVED";

    private ClientReviewApprovalPolicy() {
    }

    /**
     * 通过前未满足的条件
     *
     * @param family 本次提交的审核全貌
     * @return 未满足条件代码；为空表示可以通过
     */
    public static List<String> blockers(ClientReviewFamily family) {
        List<String> blockers = new ArrayList<>();
        if (!family.isActive()) {
            blockers.add(CASE_NOT_ACTIVE);
        }
        if (family.getChildCases().stream().anyMatch(child -> !Integer.valueOf(1).equals(child.getActiveSlot()))) {
            blockers.add(CHILD_CASE_NOT_ACTIVE);
        }
        if (family.getEvidence().stream().anyMatch(evidence -> "RECEIVED".equals(evidence.getStatus()))) {
            blockers.add(EVIDENCE_PENDING);
        }
        if (family.getRequirements().stream().anyMatch(ClientReviewApprovalPolicy::requiredNotAccepted)) {
            blockers.add(REQUIRED_MATERIAL_NOT_ACCEPTED);
        }
        if (family.getClassifications().isEmpty()) {
            blockers.add(CLASSIFICATION_NOT_RECEIVED);
        }
        return blockers;
    }

    /** 基础必需要求尚无被接受的证据。 */
    static boolean requiredNotAccepted(ReviewRequirementView requirement) {
        return requirement.getRequiredFlag() == 1 && !"ACCEPTED".equals(requirement.getStatus());
    }
}
