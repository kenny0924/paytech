package com.paytech.compliance.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.paytech.compliance.core.dal.dataobject.CaseCheck;
import com.paytech.compliance.core.dal.dataobject.CaseClassification;
import com.paytech.compliance.core.dal.dataobject.CaseDependency;
import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.compliance.core.dal.dataobject.ReturnError;
import com.paytech.compliance.core.dal.dataobject.RiskFactor;
import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.dataobject.ScreeningHitList;
import com.paytech.compliance.core.dal.dataobject.ScreeningResolution;
import com.paytech.compliance.core.dal.dataobject.ScreeningRun;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次 Client 入驻提交的审核全貌：主案件、关联人员子案件及其主体、材料、检查、筛查、评级、退回错误、时间线与冻结的程序。
 * <li>多表组合查询结果，只承载本次查询、门禁判断与命令校验所需数据；纯内存方法不访问数据库。</li>
 */
@Getter
@AllArgsConstructor
public class ClientReviewFamily {
    /** 主案件。 */
    private final DueDiligenceCase mainCase;
    /** 关联人员子案件。 */
    private final List<DueDiligenceCase> childCases;
    /** 主案件对子案件的依赖。 */
    private final List<CaseDependency> dependencies;
    /** 全部案件的主体。 */
    private final List<CaseSubject> subjects;
    /** 全部案件的材料要求及模板。 */
    private final List<ReviewRequirementView> requirements;
    /** 全部案件的证据引用。 */
    private final List<EvidenceRef> evidence;
    /** 主案件已接收的分类。 */
    private final List<CaseClassification> classifications;
    /** 全部案件的检查。 */
    private final List<CaseCheck> checks;
    /** 全部案件的筛查执行，最新在前。 */
    private final List<ScreeningRun> screeningRuns;
    /** 全部命中。 */
    private final List<ScreeningHit> hits;
    /** 命中的名单类型。 */
    private final List<ScreeningHitList> hitLists;
    /** 命中的处置记录。 */
    private final List<ScreeningResolution> resolutions;
    /** 主案件的风险因子。 */
    private final List<RiskFactor> riskFactors;
    /** 主案件最近一次退回的需求级错误。 */
    private final List<ReturnError> returnErrors;
    /** 全部案件的状态变更，按发生时间升序。 */
    private final List<CaseStateChange> stateChanges;
    /** 案件冻结的程序版本：版本 ID 到程序。 */
    private final Map<Long, ResolvedProgram> programs;

    /** 主案件与子案件。 */
    public List<DueDiligenceCase> allCases() {
        List<DueDiligenceCase> all = new ArrayList<>();
        all.add(mainCase);
        all.addAll(childCases);
        return all;
    }

    /** 全部案件 ID。 */
    public List<Long> caseIds() {
        return allCases().stream().map(DueDiligenceCase::getCaseId).toList();
    }

    /** 主案件是否仍在进行中。 */
    public boolean isActive() {
        return Integer.valueOf(1).equals(mainCase.getActiveSlot());
    }

    /** 主案件当前状态。 */
    public DueDiligenceCaseStatus mainStatus() {
        return DueDiligenceCaseStatus.ofCode(mainCase.getStatus());
    }

    /** 案件冻结的程序。 */
    public ResolvedProgram programOf(DueDiligenceCase row) {
        return programs.get(row.getDdProgramVersionId());
    }

    /** 主案件冻结的程序。 */
    public ResolvedProgram mainProgram() {
        return programOf(mainCase);
    }

    /** 案件的 PRIMARY 主体。 */
    public CaseSubject subjectOf(long caseId) {
        return subjects.stream().filter(row -> row.getCaseId() == caseId && Integer.valueOf(1).equals(row.getPrimarySlot())).findFirst()
            .orElseThrow();
    }

    /** 按 ID 查找家族内证据。 */
    public Optional<EvidenceRef> findEvidence(long evidenceId) {
        return evidence.stream().filter(row -> row.getEvidenceId() == evidenceId).findFirst();
    }

    /** 按 ID 查找家族内材料要求。 */
    public Optional<ReviewRequirementView> findRequirement(long requirementId) {
        return requirements.stream().filter(row -> row.getRequirementId() == requirementId).findFirst();
    }

    /** 按 ID 查找家族内检查。 */
    public Optional<CaseCheck> findCheck(long checkId) {
        return checks.stream().filter(row -> row.getCheckId() == checkId).findFirst();
    }

    /** 按 ID 查找家族内命中。 */
    public Optional<ScreeningHit> findHit(long hitId) {
        return hits.stream().filter(row -> row.getHitId() == hitId).findFirst();
    }

    /** 命中的处置记录，按版本升序。 */
    public List<ScreeningResolution> resolutionsOf(long hitId) {
        return resolutions.stream().filter(row -> row.getHitId() == hitId).toList();
    }

    /** 命中的名单类型。 */
    public List<String> listTypesOf(long hitId) {
        return hitLists.stream().filter(row -> row.getHitId() == hitId).map(ScreeningHitList::getListType).toList();
    }
}
