package com.paytech.compliance.service.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.CaseDependency;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.ProgramRequirement;
import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.dataobject.ScreeningRun;
import com.paytech.compliance.core.enums.ClientReviewErrorCode;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ResolvedProgram;
import com.paytech.compliance.domain.ReviewRequirementView;
import com.paytech.compliance.domainservice.repository.CaseCheckRepository;
import com.paytech.compliance.domainservice.repository.CaseClassificationRepository;
import com.paytech.compliance.domainservice.repository.CaseDependencyRepository;
import com.paytech.compliance.domainservice.repository.CaseRequirementRepository;
import com.paytech.compliance.domainservice.repository.CaseStateChangeRepository;
import com.paytech.compliance.domainservice.repository.CaseSubjectRepository;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.repository.EvidenceRefRepository;
import com.paytech.compliance.domainservice.repository.ReturnErrorRepository;
import com.paytech.compliance.domainservice.repository.RiskFactorRepository;
import com.paytech.compliance.domainservice.repository.ScreeningHitListRepository;
import com.paytech.compliance.domainservice.repository.ScreeningHitRepository;
import com.paytech.compliance.domainservice.repository.ScreeningResolutionRepository;
import com.paytech.compliance.domainservice.repository.ScreeningRunRepository;
import com.paytech.compliance.domainservice.service.DueDiligenceProgramService;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** 组合读取一次 Client 入驻提交的审核全貌；只读，不加锁，允许观察相邻已提交状态。 */
@Component
@AllArgsConstructor
public class ClientReviewFamilyLoader {
    private final CaseCheckRepository checks;
    private final ReturnErrorRepository returnErrors;
    private final RiskFactorRepository riskFactors;
    private final CaseSubjectRepository subjects;
    private final EvidenceRefRepository evidence;
    private final ScreeningRunRepository runs;
    private final ScreeningHitRepository hits;
    private final CaseDependencyRepository dependencies;
    private final CaseRequirementRepository requirements;
    private final DueDiligenceCaseRepository cases;
    private final CaseStateChangeRepository stateChanges;
    private final ScreeningHitListRepository hitLists;
    private final DueDiligenceProgramService programs;
    private final CaseClassificationRepository classifications;
    private final ScreeningResolutionRepository resolutions;

    /**
     * 按主案件 ID 读取
     *
     * @param caseId 主案件 ID
     * @return 审核全貌
     *         <li>不存在或不是 Client 入驻主案件时 404；冻结的程序版本缺失为系统错误。</li>
     */
    public ClientReviewFamily load(long caseId) {
        DueDiligenceCase main = cases.findById(caseId);
        if (main == null || !ReviewCodes.ROLE_CLIENT.equals(main.getRoleType())
            || !ReviewCodes.NAMESPACE_CLIENT_APPLICATION.equals(main.getSourceNamespace())) {
            throw new FailException(ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND, "审核案件不存在: " + caseId);
        }
        List<CaseDependency> links = dependencies.findByParent(caseId);
        List<DueDiligenceCase> children = cases.findByIds(links.stream().map(CaseDependency::getChildCaseId).distinct().toList());
        List<Long> ids = new ArrayList<>();
        ids.add(caseId);
        children.forEach(child -> ids.add(child.getCaseId()));
        Map<Long, ResolvedProgram> frozen = programs.load(versionIds(main, children));
        List<ScreeningRun> runRows = runs.findByCaseIds(ids);
        List<ScreeningHit> hitRows = hits.findByRunIds(runRows.stream().map(ScreeningRun::getScreeningRunId).toList());
        List<Long> hitIds = hitRows.stream().map(ScreeningHit::getHitId).toList();
        return new ClientReviewFamily(main, children, links, subjects.findByCaseIds(ids), requirementViews(ids, frozen),
            evidence.findByCaseIds(ids), classifications.findByCase(caseId), checks.findByCaseIds(ids), runRows, hitRows,
            hitLists.findByHitIds(hitIds), resolutions.findByHitIds(hitIds), riskFactors.findByCase(caseId),
            returnErrors.findByCase(caseId), stateChanges.findByCaseIds(ids), frozen);
    }

    /** 主案件与子案件冻结的全部程序版本。 */
    static List<Long> versionIds(DueDiligenceCase main, List<DueDiligenceCase> children) {
        List<Long> ids = new ArrayList<>();
        ids.add(main.getDdProgramVersionId());
        children.forEach(child -> ids.add(child.getDdProgramVersionId()));
        return ids.stream().distinct().toList();
    }

    /** 材料要求视图并补齐冻结程序中的允许类型。 */
    static List<ReviewRequirementView> requirementViewsWith(List<ReviewRequirementView> views, Map<Long, ResolvedProgram> frozen) {
        for (ReviewRequirementView view : views) {
            ProgramRequirement template = frozen.values().stream().flatMap(program -> program.getRequirements().stream())
                .filter(row -> row.getProgramRequirementId() == view.getProgramRequirementId()).findFirst()
                .orElseThrow(() -> new ErrorException("材料要求的程序模板缺失: " + view.getRequirementId()));
            view.setDocumentTypes(template.getDocumentTypes());
        }
        return views;
    }

    /** 读取并补齐材料要求视图。 */
    List<ReviewRequirementView> requirementViews(List<Long> caseIds, Map<Long, ResolvedProgram> frozen) {
        return requirementViewsWith(requirements.findViewsByCaseIds(caseIds), frozen);
    }
}
