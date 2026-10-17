package com.paytech.compliance.domainservice.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.compliance.core.dal.dataobject.CaseClassification;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.compliance.domain.ClientReviewCaseBundle;
import com.paytech.compliance.domain.ClientReviewContext;
import com.paytech.compliance.domain.ClientReviewDecisionContext;
import com.paytech.compliance.domain.ReviewRequirementView;
import com.paytech.compliance.domainservice.repository.CaseClassificationRepository;
import com.paytech.compliance.domainservice.repository.CaseDependencyRepository;
import com.paytech.compliance.domainservice.repository.CaseRequirementRepository;
import com.paytech.compliance.domainservice.repository.CaseStateChangeRepository;
import com.paytech.compliance.domainservice.repository.CaseSubjectRepository;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.repository.EvidenceRefRepository;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.handler.ClientReviewResultHandler;

import lombok.AllArgsConstructor;

/** 审核登记与审核命令的本地原子保存。 */
@Service
@AllArgsConstructor
public class ClientReviewServiceImpl implements ClientReviewService {
    private final CaseSubjectRepository subjects;
    private final EvidenceRefRepository evidence;
    private final CaseDependencyRepository dependencies;
    private final CaseRequirementRepository requirements;
    private final CaseStateChangeRepository stateChanges;
    private final CompensationEventRegistrar events;
    private final DueDiligenceCaseRepository cases;
    private final QualificationDecisionRepository decisions;
    private final PlatformTransactionManager transactionManager;
    private final CaseClassificationRepository classifications;

    /** 一个 READ COMMITTED 短事务保存全部登记事实。 */
    @Override
    public void register(ClientReviewContext context) {
        transaction().executeWithoutResult(status -> {
            save(context.getMainCase());
            context.getChildCases().forEach(this::save);
            context.getDependencies().forEach(dependencies::insert);
        });
    }

    /** 保存单个案件的全部行；外层事务负责提交。 */
    void save(ClientReviewCaseBundle bundle) {
        cases.insert(bundle.getReviewCase());
        subjects.insert(bundle.getSubject());
        bundle.getRequirements().forEach(requirements::insert);
        bundle.getEvidence().forEach(evidence::insert);
        stateChanges.insert(bundle.getStateChange());
    }

    /** 递增主任务版本后条件更新证据，要求状态变化时一并更新。 */
    @Override
    public void reviewEvidence(DueDiligenceCase mainCase, EvidenceRef evidence, String outcome, ReviewRequirementView requirement,
        String requirementStatus, Instant now) {
        transaction().executeWithoutResult(status -> {
            cases.touch(mainCase, now);
            this.evidence.review(evidence, outcome, now);
            if (requirementStatus != null) {
                requirements.updateStatus(requirement, requirementStatus, now);
            }
        });
    }

    /** 递增主任务版本后插入全部分类。 */
    @Override
    public void saveClassifications(DueDiligenceCase mainCase, List<CaseClassification> rows, Instant now) {
        transaction().executeWithoutResult(status -> {
            cases.touch(mainCase, now);
            rows.forEach(classifications::insert);
        });
    }

    /** 子任务先于主任务结束；主任务版本条件保证读取门禁后没有其他命令插入。 */
    @Override
    public void decide(ClientReviewDecisionContext context) {
        transaction().executeWithoutResult(status -> {
            for (DueDiligenceCase child : context.getChildCases()) {
                cases.close(child, context.getAction().getChildStatus(), null, context.getResolutionNote(), null, context.getNow());
            }
            cases.close(context.getMainCase(), context.getAction().getCaseStatus(), context.getPublicNote(),
                context.getResolutionNote(), context.getReturnSection(), context.getNow());
            context.getDecisions().forEach(decisions::insert);
            context.getStateChanges().forEach(stateChanges::insert);
            events.register(ClientReviewResultHandler.buildEvent(context.getMainCase().getCaseId()));
        });
    }

    /** READ COMMITTED、5 秒超时的本地短事务。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
        return transaction;
    }
}
