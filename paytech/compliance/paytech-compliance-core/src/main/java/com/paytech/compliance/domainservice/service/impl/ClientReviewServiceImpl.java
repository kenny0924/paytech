package com.paytech.compliance.domainservice.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.compliance.core.dal.dataobject.CaseClassification;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.dataobject.ScreeningHitList;
import com.paytech.compliance.core.dal.dataobject.ScreeningRun;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.CaseTransitionContext;
import com.paytech.compliance.domain.CheckResultContext;
import com.paytech.compliance.domain.ClientReviewCaseBundle;
import com.paytech.compliance.domain.ClientReviewContext;
import com.paytech.compliance.domain.ClientReviewDecisionContext;
import com.paytech.compliance.domain.EvidenceReviewContext;
import com.paytech.compliance.domain.HitResolutionContext;
import com.paytech.compliance.domain.ReviewCommandContext;
import com.paytech.compliance.domain.ReviewRequirementView;
import com.paytech.compliance.domain.RiskRatingContext;
import com.paytech.compliance.domainservice.repository.CaseCheckRepository;
import com.paytech.compliance.domainservice.repository.CaseClassificationRepository;
import com.paytech.compliance.domainservice.repository.CaseDependencyRepository;
import com.paytech.compliance.domainservice.repository.CaseRequirementRepository;
import com.paytech.compliance.domainservice.repository.CaseStateChangeRepository;
import com.paytech.compliance.domainservice.repository.CaseSubjectRepository;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.repository.EvidenceRefRepository;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.compliance.domainservice.repository.ReturnErrorRepository;
import com.paytech.compliance.domainservice.repository.RiskFactorRepository;
import com.paytech.compliance.domainservice.repository.ScreeningHitListRepository;
import com.paytech.compliance.domainservice.repository.ScreeningHitRepository;
import com.paytech.compliance.domainservice.repository.ScreeningResolutionRepository;
import com.paytech.compliance.domainservice.repository.ScreeningRunRepository;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.handler.CaseScreeningHandler;
import com.paytech.compliance.service.handler.ClientReviewResultHandler;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;

import lombok.AllArgsConstructor;

/** 审核登记、审核命令、撤回 / 过期与筛查结果的本地原子保存。 */
@Service
@AllArgsConstructor
public class ClientReviewServiceImpl implements ClientReviewService {
    private final CaseCheckRepository checks;
    private final ScreeningRunRepository runs;
    private final ScreeningHitRepository hits;
    private final CaseSubjectRepository subjects;
    private final EvidenceRefRepository evidence;
    private final RiskFactorRepository riskFactors;
    private final ReturnErrorRepository returnErrors;
    private final CaseDependencyRepository dependencies;
    private final CaseRequirementRepository requirements;
    private final CaseStateChangeRepository stateChanges;
    private final CompensationEventRegistrar events;
    private final DueDiligenceCaseRepository cases;
    private final ScreeningHitListRepository hitLists;
    private final QualificationDecisionRepository decisions;
    private final PlatformTransactionManager transactionManager;
    private final CaseClassificationRepository classifications;
    private final ScreeningResolutionRepository resolutions;

    /** 一个短事务保存全部登记事实并登记筛查任务。 */
    @Override
    public void register(ClientReviewContext context) {
        transaction().executeWithoutResult(status -> {
            save(context.getMainCase());
            context.getChildCases().forEach(this::save);
            context.getDependencies().forEach(dependencies::insert);
            events.register(CaseScreeningHandler.buildEvent(context.getMainCase().getReviewCase().getCaseId(),
                CaseScreeningHandler.INITIAL_RUN));
        });
    }

    /** 保存单个案件的全部行；外层事务负责提交。 */
    void save(ClientReviewCaseBundle bundle) {
        cases.insert(bundle.getReviewCase());
        subjects.insert(bundle.getSubject());
        bundle.getRequirements().forEach(requirements::insert);
        bundle.getEvidence().forEach(evidence::insert);
        bundle.getChecks().forEach(checks::insert);
        stateChanges.insert(bundle.getStateChange());
    }

    /** 推进或递增主案件版本后条件更新证据，要求状态变化时一并更新。 */
    @Override
    public void reviewEvidence(EvidenceReviewContext context) {
        transaction().executeWithoutResult(status -> {
            begin(context.getCommand());
            evidence.review(context.getEvidence(), context.getOutcome(), context.getRejectReasonCode(), context.getNote(),
                context.getCommand().getActorId(), context.getCommand().getNow());
            if (context.getRequirementStatus() != null) {
                requirements.updateStatus(context.getRequirement(), context.getRequirementStatus(), context.getCommand().getNow());
            }
        });
    }

    /** 推进主案件后豁免要求。 */
    @Override
    public void waive(ReviewCommandContext command, ReviewRequirementView requirement, String reasonCode, String note) {
        transaction().executeWithoutResult(status -> {
            begin(command);
            requirements.waive(requirement, command.getActorId(), reasonCode, note, command.getNow());
        });
    }

    /** 推进主案件后录入检查结论。 */
    @Override
    public void recordCheck(CheckResultContext context) {
        ReviewCommandContext command = context.getCommand();
        transaction().executeWithoutResult(status -> {
            begin(command);
            checks.record(context.getCheck(), context.getOutcome(), context.getReasonCode(), context.getEvidenceRef(),
                context.getNote(), command.getActorId(), context.getValidUntil(), ReviewCodes.OPERATED_BY_PLATFORM,
                command.getOnBehalfOfPartnerId(), command.getNow());
        });
    }

    /** 推进主案件后追加处置并按需关闭命中。 */
    @Override
    public void resolveHit(HitResolutionContext context) {
        ReviewCommandContext command = context.getCommand();
        transaction().executeWithoutResult(status -> {
            begin(command);
            if (context.isClosesHit()) {
                hits.resolve(context.getHit(), command.getNow());
            } else {
                hits.touch(context.getHit(), command.getNow());
            }
            resolutions.insert(context.getResolution());
        });
    }

    /** 推进主案件后替换评级与审核员因子。 */
    @Override
    public void rateRisk(RiskRatingContext context) {
        ReviewCommandContext command = context.getCommand();
        transaction().executeWithoutResult(status -> {
            begin(command);
            DueDiligenceCase main = command.getMainCase();
            cases.updateRiskRating(main, context.getRiskRating(), context.getNote(), command.getActorId(), command.getNow());
            riskFactors.deleteByCaseAndSource(main.getCaseId(), ReviewCodes.FACTOR_SOURCE_REVIEWER);
            context.getFactors().forEach(riskFactors::insert);
        });
    }

    /** 推进主案件后写入推荐。 */
    @Override
    public void recommend(ReviewCommandContext command, String recommendation, String note) {
        transaction().executeWithoutResult(status -> {
            begin(command);
            cases.updateRecommendation(command.getMainCase(), recommendation, note, command.getActorId(), command.getNow());
        });
    }

    /** 推进主案件后登记重跑筛查任务。 */
    @Override
    public void requestScreening(ReviewCommandContext command, String rerunRef) {
        transaction().executeWithoutResult(status -> {
            begin(command);
            events.register(CaseScreeningHandler.buildEvent(command.getMainCase().getCaseId(), rerunRef));
        });
    }

    /** 递增主案件版本后插入全部分类。 */
    @Override
    public void saveClassifications(DueDiligenceCase mainCase, List<CaseClassification> rows, Instant now) {
        transaction().executeWithoutResult(status -> {
            cases.touch(mainCase, now);
            rows.forEach(classifications::insert);
        });
    }

    /** 子案件先于主案件结束；主案件版本条件保证读取门禁后没有其他命令插入。 */
    @Override
    public void decide(ClientReviewDecisionContext context) {
        transaction().executeWithoutResult(status -> {
            for (DueDiligenceCase child : context.getChildCases()) {
                cases.close(child, context.getAction().getChildStatus(), context.getChildClosing(), context.getNow());
            }
            cases.close(context.getMainCase(), context.getAction().getCaseStatus(), context.getClosing(), context.getNow());
            context.getDecisions().forEach(decisions::insert);
            context.getReturnErrors().forEach(returnErrors::insert);
            context.getStateChanges().forEach(stateChanges::insert);
            events.register(ClientReviewResultHandler.buildEvent(context.getMainCase().getCaseId()));
        });
    }

    /** 迁移一组案件；过期时登记结果发布。 */
    @Override
    public void transition(CaseTransitionContext context) {
        transaction().executeWithoutResult(status -> {
            context.getCases().forEach(row -> cases.transition(row, context.getToStatus(), context.getNow()));
            context.getStateChanges().forEach(stateChanges::insert);
            if (context.isPublishResult()) {
                events.register(ClientReviewResultHandler.buildEvent(context.getMainCase().getCaseId()));
            }
        });
    }

    /** 新建一次筛查执行。 */
    @Override
    public void startScreeningRun(ScreeningRun run) {
        transaction().executeWithoutResult(status -> runs.insert(run));
    }

    /** 结束执行；成功时写入命中并取代旧命中。 */
    @Override
    public void completeScreeningRun(ScreeningRun run, String status, String executionRef, List<ScreeningHit> hitRows,
        List<ScreeningHitList> hitListRows, Instant now) {
        transaction().executeWithoutResult(tx -> {
            runs.complete(run, status, executionRef, now);
            if (ReviewCodes.RUN_SUCCEEDED.equals(status)) {
                hits.supersedeOthers(run.getCaseSubjectId(), run.getProgramScreeningId(), run.getScreeningRunId(), now);
                hitRows.forEach(hits::insert);
                hitListRows.forEach(hitLists::insert);
            }
        });
    }

    /** 主案件仍为 SUBMITTED 时推进为 IN_REVIEW 并追加状态变更，否则递增版本；外层事务负责提交。 */
    void begin(ReviewCommandContext command) {
        if (command.getStartReview() != null) {
            cases.startReview(command.getMainCase(), command.getNow());
            stateChanges.insert(command.getStartReview());
            return;
        }
        cases.touch(command.getMainCase(), command.getNow());
    }

    /** READ COMMITTED、5 秒超时的本地短事务；已有事务时加入。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
        return transaction;
    }
}
