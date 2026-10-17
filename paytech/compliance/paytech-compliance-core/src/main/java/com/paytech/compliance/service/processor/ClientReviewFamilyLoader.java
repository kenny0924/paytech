package com.paytech.compliance.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.compliance.core.enums.ClientReviewErrorCode;
import com.paytech.compliance.core.dal.dataobject.CaseDependency;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domainservice.repository.CaseClassificationRepository;
import com.paytech.compliance.domainservice.repository.CaseDependencyRepository;
import com.paytech.compliance.domainservice.repository.CaseRequirementRepository;
import com.paytech.compliance.domainservice.repository.CaseSubjectRepository;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.repository.EvidenceRefRepository;

import lombok.AllArgsConstructor;

/** 组合读取一次 Client 入驻提交的审核全貌；只读，不加锁。 */
@Component
@AllArgsConstructor
public class ClientReviewFamilyLoader {
    private final CaseSubjectRepository subjects;
    private final EvidenceRefRepository evidence;
    private final CaseDependencyRepository dependencies;
    private final CaseRequirementRepository requirements;
    private final DueDiligenceCaseRepository cases;
    private final CaseClassificationRepository classifications;

    /**
     * 按主案件 ID 读取
     *
     * @param caseId 主案件 ID
     * @return 审核全貌
     *         <li>不存在或不是 Client 入驻主案件时 404。</li>
     */
    public ClientReviewFamily load(long caseId) {
        DueDiligenceCase main = cases.findById(caseId);
        if (main == null || !"CLIENT".equals(main.getRoleType()) || !RegisterClientReviewProcessor.NAMESPACE.equals(main.getSourceNamespace())) {
            throw new FailException(ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND);
        }
        List<CaseDependency> links = dependencies.findByParent(caseId);
        List<DueDiligenceCase> children = cases.findByIds(links.stream().map(CaseDependency::getChildCaseId).distinct().toList());
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(caseId);
        children.forEach(child -> ids.add(child.getCaseId()));
        return new ClientReviewFamily(main, children, links, subjects.findByCaseIds(ids), requirements.findViewsByCaseIds(ids),
            evidence.findByCaseIds(ids), classifications.findByCase(caseId));
    }
}
