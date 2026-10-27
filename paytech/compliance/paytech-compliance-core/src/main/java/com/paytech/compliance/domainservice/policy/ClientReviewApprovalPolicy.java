package com.paytech.compliance.domainservice.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.paytech.compliance.core.dal.dataobject.CaseCheck;
import com.paytech.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.ProgramCheck;
import com.paytech.compliance.core.dal.dataobject.ProgramScreening;
import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.dataobject.ScreeningResolution;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.core.enums.ScreeningHitResolution;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ResolvedProgram;
import com.paytech.compliance.domain.ReviewRequirementView;

/**
 * Client 入驻审核的决定门禁（technique CP-6）；材料是否充分由运营逐份核验，系统只检查各审核项是否完成。
 * <li>批准：必需材料 ACCEPTED 或 WAIVED、无待核验材料、必需检查已录入且阻断检查为有效 PASS、必需筛查已成功执行、无 OPEN 命中、
 * 无 TRUE_POSITIVE_REJECT 命中、子案件进行中、分类已确认、CRR 已评；程序要求四眼时须有 APPROVE 推荐且决定人不是推荐人。</li>
 * <li>拒绝：程序要求四眼时须有 REJECT 推荐且决定人不是推荐人；其他审核项不阻断拒绝。</li>
 * <li>无副作用，不访问数据库或外部服务。</li>
 */
public final class ClientReviewApprovalPolicy {
    /** 主案件已结束。 */
    public static final String CASE_NOT_ACTIVE = "CASE_NOT_ACTIVE";
    /** 子案件不在进行中。 */
    public static final String CHILD_CASE_NOT_ACTIVE = "CHILD_CASE_NOT_ACTIVE";
    /** 仍有未核验的材料。 */
    public static final String EVIDENCE_PENDING = "EVIDENCE_PENDING";
    /** 必需材料未被接受或豁免。 */
    public static final String REQUIRED_MATERIAL_NOT_ACCEPTED = "REQUIRED_MATERIAL_NOT_ACCEPTED";
    /** 尚未收到已确认分类。 */
    public static final String CLASSIFICATION_NOT_RECEIVED = "CLASSIFICATION_NOT_RECEIVED";
    /** 必需检查未录入，或阻断检查不是有效 PASS。 */
    public static final String CHECK_NOT_PASSED = "CHECK_NOT_PASSED";
    /** 必需筛查尚无成功执行。 */
    public static final String SCREENING_INCOMPLETE = "SCREENING_INCOMPLETE";
    /** 仍有待处置命中。 */
    public static final String SCREENING_HIT_OPEN = "SCREENING_HIT_OPEN";
    /** 命中被确认为须拒绝的真实匹配。 */
    public static final String SCREENING_TRUE_MATCH = "SCREENING_TRUE_MATCH";
    /** 尚未手工评级。 */
    public static final String RISK_RATING_MISSING = "RISK_RATING_MISSING";
    /** 四眼：尚无推荐。 */
    public static final String RECOMMENDATION_MISSING = "RECOMMENDATION_MISSING";
    /** 四眼：推荐结论与本次决定不一致（批准需 APPROVE 推荐）。 */
    public static final String RECOMMENDATION_NOT_APPROVE = "RECOMMENDATION_NOT_APPROVE";
    /** 四眼：拒绝需 REJECT 推荐。 */
    public static final String RECOMMENDATION_NOT_REJECT = "RECOMMENDATION_NOT_REJECT";
    /** 四眼：决定人与推荐人相同。 */
    public static final String FOUR_EYES_SAME_PERSON = "FOUR_EYES_SAME_PERSON";

    private ClientReviewApprovalPolicy() {
    }

    /**
     * 批准前未满足的条件
     *
     * @param family 本次提交的审核全貌
     * @param actorId 决定人；查询详情时为当前查看人，可空表示不判断同人
     * @param now 判断时间（检查结论有效期）
     * @return 未满足条件代码；为空表示可以批准
     */
    public static List<String> approvalBlockers(ClientReviewFamily family, Long actorId, Instant now) {
        List<String> blockers = new ArrayList<>();
        if (!family.isActive()) {
            blockers.add(CASE_NOT_ACTIVE);
        }
        if (family.getChildCases().stream().anyMatch(child -> !Integer.valueOf(1).equals(child.getActiveSlot()))) {
            blockers.add(CHILD_CASE_NOT_ACTIVE);
        }
        materialBlockers(family, blockers);
        if (family.getClassifications().isEmpty()) {
            blockers.add(CLASSIFICATION_NOT_RECEIVED);
        }
        if (family.allCases().stream().anyMatch(row -> !checksPassed(family, row, now))) {
            blockers.add(CHECK_NOT_PASSED);
        }
        screeningBlockers(family, blockers);
        if (family.getMainCase().getRiskRating() == null) {
            blockers.add(RISK_RATING_MISSING);
        }
        blockers.addAll(fourEyesBlockers(family, actorId, ReviewCodes.RECOMMEND_APPROVE, RECOMMENDATION_NOT_APPROVE));
        return blockers;
    }

    /**
     * 拒绝前未满足的条件（仅四眼）
     *
     * @param family 本次提交的审核全貌
     * @param actorId 决定人
     * @return 未满足条件代码
     */
    public static List<String> rejectionBlockers(ClientReviewFamily family, Long actorId) {
        return fourEyesBlockers(family, actorId, ReviewCodes.RECOMMEND_REJECT, RECOMMENDATION_NOT_REJECT);
    }

    /** 材料核验与必需材料。 */
    static void materialBlockers(ClientReviewFamily family, List<String> blockers) {
        List<Long> waived = family.getRequirements().stream().filter(row -> ReviewCodes.WAIVED.equals(row.getStatus()))
            .map(ReviewRequirementView::getRequirementId).toList();
        if (family.getEvidence().stream()
            .anyMatch(row -> ReviewCodes.RECEIVED.equals(row.getStatus()) && !waived.contains(row.getRequirementId()))) {
            blockers.add(EVIDENCE_PENDING);
        }
        if (family.getRequirements().stream().anyMatch(row -> row.isRequired() && !ReviewCodes.ACCEPTED.equals(row.getStatus())
            && !ReviewCodes.WAIVED.equals(row.getStatus()))) {
            blockers.add(REQUIRED_MATERIAL_NOT_ACCEPTED);
        }
    }

    /** 案件的必需检查已录入，阻断检查为有效期内 PASS。 */
    static boolean checksPassed(ClientReviewFamily family, DueDiligenceCase row, Instant now) {
        ResolvedProgram program = family.programOf(row);
        for (ProgramCheck template : program.getChecks()) {
            CaseCheck check = family.getChecks().stream()
                .filter(item -> item.getCaseId() == row.getCaseId() && item.getProgramCheckId() == template.getProgramCheckId())
                .findFirst().orElse(null);
            boolean completed = check != null && ReviewCodes.CHECK_COMPLETED.equals(check.getStatus());
            if (template.isRequired() && !completed) {
                return false;
            }
            boolean pass = completed && ReviewCodes.OUTCOME_PASS.equals(check.getOutcome())
                && (check.getValidUntil() == null || check.getValidUntil().isAfter(now));
            if (template.isBlocking() && !pass) {
                return false;
            }
        }
        return true;
    }

    /** 筛查完成度与命中处置。 */
    static void screeningBlockers(ClientReviewFamily family, List<String> blockers) {
        boolean incomplete = family.allCases().stream().anyMatch(row -> !screeningsCompleted(family, row));
        if (incomplete) {
            blockers.add(SCREENING_INCOMPLETE);
        }
        if (family.getHits().stream().anyMatch(hit -> ReviewCodes.HIT_OPEN.equals(hit.getStatus()))) {
            blockers.add(SCREENING_HIT_OPEN);
        }
        if (family.getHits().stream().anyMatch(hit -> isTrueMatch(family, hit))) {
            blockers.add(SCREENING_TRUE_MATCH);
        }
    }

    /** 案件主体的每个必需筛查模板至少有一次成功执行。 */
    static boolean screeningsCompleted(ClientReviewFamily family, DueDiligenceCase row) {
        CaseSubject subject = family.subjectOf(row.getCaseId());
        for (ProgramScreening template : family.programOf(row).getScreenings()) {
            boolean succeeded = family.getScreeningRuns().stream()
                .anyMatch(run -> run.getCaseSubjectId() == subject.getCaseSubjectId()
                    && run.getProgramScreeningId() == template.getProgramScreeningId()
                    && ReviewCodes.RUN_SUCCEEDED.equals(run.getStatus()));
            if (template.isRequired() && !succeeded) {
                return false;
            }
        }
        return true;
    }

    /** 未被取代的命中最近一次处置为 TRUE_POSITIVE_REJECT。 */
    static boolean isTrueMatch(ClientReviewFamily family, ScreeningHit hit) {
        if (ReviewCodes.HIT_SUPERSEDED.equals(hit.getStatus())) {
            return false;
        }
        List<ScreeningResolution> rows = family.resolutionsOf(hit.getHitId());
        return !rows.isEmpty()
            && ScreeningHitResolution.TRUE_POSITIVE_REJECT.getCode().equals(rows.getLast().getResolution());
    }

    /** 程序要求四眼时的推荐与同人检查。 */
    static List<String> fourEyesBlockers(ClientReviewFamily family, Long actorId, String expected, String mismatch) {
        if (!family.mainProgram().getVersion().requiresFourEyes()) {
            return List.of();
        }
        DueDiligenceCase main = family.getMainCase();
        if (main.getRecommendation() == null) {
            return List.of(RECOMMENDATION_MISSING);
        }
        List<String> blockers = new ArrayList<>();
        if (!expected.equals(main.getRecommendation())) {
            blockers.add(mismatch);
        }
        if (actorId != null && Objects.equals(actorId, main.getRecommendedBy())) {
            blockers.add(FOUR_EYES_SAME_PERSON);
        }
        return blockers;
    }
}
