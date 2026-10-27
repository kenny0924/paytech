package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_CLOSED;
import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_READY;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.ReturnError;
import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.core.enums.QualificationDecisionSource;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.CaseClosing;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.domain.ClientReviewDecisionContext;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.QualificationChangeContext;
import com.paytech.compliance.domainservice.policy.ClientReviewApprovalPolicy;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.domainservice.service.QualificationService;
import com.paytech.compliance.service.processor.request.DecideClientReviewRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * O07 退回、O08 批准、O09 最终拒绝：结束本次提交的主案件与仍在进行的子案件，并登记结果发布
 * <li>退回：需求级错误与补件期限（1—90 天）写入主案件；主 / 子案件 RETURNED 释放进行中占位，客户重提时新建案件串联（S-01）。</li>
 * <li>批准前按 ClientReviewApprovalPolicy 判断门禁（含四眼）；拒绝只判断四眼；不满足 422 CLIENT_REVIEW_NOT_READY 并列出未满足项。</li>
 * <li>决定冻结档次、评级、辖区、责任方、推荐人、决定人、操作方与代操作 PayFac；四眼关闭的程序记 four_eyes_waived=1。</li>
 * <li>案件已以同一结论结束直接返回；已以其他结论结束 409。主案件按读取时版本条件结束，期间有其他审核命令时整体回滚 409。</li>
 * <li>主案件决定同事务投影到 Client 角色资格并登记 QualificationChanged：批准新建或切换为 APPROVED（按程序复审间隔排定下次复审），
 * 拒绝只把已有的 APPROVED 资格切换为 REJECTED；关联人员子案件不投影。</li>
 */
@Component
@AllArgsConstructor
public class DecideClientReviewProcessor extends AbstractProcessor<DecideClientReviewRequest, ClientReviewCommandResponse> {
    private final ClientReviewService reviews;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;
    private final QualificationService qualificationService;
    private final RoleQualificationRepository qualifications;
    private final PlatformTransactionManager transactionManager;

    /** 退回要求错误、期限与客户可见说明；批准与拒绝要求内部理由且不能带退回字段。 */
    @Override
    protected void validate(DecideClientReviewRequest request) {
        if (request.getAction() == ClientReviewAction.RETURN) {
            if (request.getErrors().isEmpty() || request.getDueDays() == null || request.getDueDays() < 1 || request.getDueDays() > 90
                || request.getPublicNote() == null || request.getPublicNote().isBlank()) {
                throw new BizCheckFailException("退回必须填写需求级错误、1—90 天补件期限与客户可见说明");
            }
            return;
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BizCheckFailException("最终决定必须填写审核理由");
        }
        if (request.getReturnSection() != null || !request.getErrors().isEmpty() || request.getDueDays() != null) {
            throw new BizCheckFailException("只有退回可以指定分区、需求级错误与补件期限");
        }
    }

    /** 当前状态与门禁校验后原子保存结论与资格投影。 */
    @Override
    protected ClientReviewCommandResponse processRequest(DecideClientReviewRequest request) {
        // 当前状态与门禁
        ClientReviewFamily family = families.load(request.getCaseId());
        DueDiligenceCase main = family.getMainCase();
        if (!family.isActive()) {
            if (request.getAction().getCaseStatus().equals(main.getStatus())) {
                return new ClientReviewCommandResponse(main.getCaseId(), main.getStatus());
            }
            throw new FailException(CLIENT_REVIEW_CLOSED, "审核案件已结束: " + main.getCaseId() + ", 状态: " + main.getStatus());
        }
        Instant now = support.now();
        checkGate(request, family, now);

        // 构建并原子保存结论与资格投影
        ClientReviewDecisionContext context = buildContext(request, family, now);
        transaction().executeWithoutResult(status -> {
            reviews.decide(context);
            if (context.getQualificationChange() != null) {
                qualificationService.applyProjection(context.getQualificationChange());
            }
        });
        return new ClientReviewCommandResponse(main.getCaseId(), main.getStatus());
    }

    /** 批准判断完整门禁，拒绝判断四眼，退回不判断。 */
    void checkGate(DecideClientReviewRequest request, ClientReviewFamily family, Instant now) {
        List<String> blockers = switch (request.getAction()) {
            case APPROVE -> ClientReviewApprovalPolicy.approvalBlockers(family, request.getActorId(), now);
            case REJECT -> ClientReviewApprovalPolicy.rejectionBlockers(family, request.getActorId());
            case RETURN -> List.of();
        };
        if (!blockers.isEmpty()) {
            throw new FailException(CLIENT_REVIEW_NOT_READY, "审核尚未满足决定条件: " + String.join(",", blockers));
        }
    }

    /** 一次构造全部要结束的案件、决定、退回错误、状态变更与资格投影。 */
    ClientReviewDecisionContext buildContext(DecideClientReviewRequest request, ClientReviewFamily family, Instant now) {
        ClientReviewAction action = request.getAction();
        DueDiligenceCase main = family.getMainCase();
        List<DueDiligenceCase> children = family.getChildCases().stream()
            .filter(child -> Integer.valueOf(1).equals(child.getActiveSlot())).toList();
        List<QualificationDecision> decisions = new ArrayList<>();
        List<CaseStateChange> changes = new ArrayList<>();
        changes.add(support.stateChange(main, action.getCaseStatus(), action.name(), request.getReasonCode(), request.getActorId(),
            request.getOnBehalfOfPartnerId(), now));
        if (action.getDecisionOutcome() != null) {
            decisions.add(decision(request, family, main, now));
        }
        for (DueDiligenceCase child : children) {
            String childAction = action == ClientReviewAction.REJECT ? "WITHDRAW_WITH_PARENT" : action.name();
            changes.add(support.stateChange(child, action.getChildStatus(), childAction, request.getReasonCode(), request.getActorId(),
                request.getOnBehalfOfPartnerId(), now));
            if (action == ClientReviewAction.APPROVE) {
                decisions.add(decision(request, family, child, now));
            }
        }
        String reason = action == ClientReviewAction.RETURN ? null : request.getReason();
        Instant due = action == ClientReviewAction.RETURN ? now.plus(request.getDueDays(), ChronoUnit.DAYS) : null;
        CaseClosing closing = new CaseClosing(request.getPublicNote(), reason, request.getReturnSection(), due,
            ReviewCodes.OPERATED_BY_PLATFORM, request.getOnBehalfOfPartnerId());
        CaseClosing childClosing = new CaseClosing(null, reason, null, due, ReviewCodes.OPERATED_BY_PLATFORM,
            request.getOnBehalfOfPartnerId());
        QualificationChangeContext projection = decisions.isEmpty() ? null : projection(request, family, decisions.getFirst());
        return new ClientReviewDecisionContext(main, children, action, closing, childClosing, decisions, returnErrors(request, main, now),
            changes, projection, now);
    }

    /** 退回的需求级错误，按请求顺序编号。 */
    List<ReturnError> returnErrors(DecideClientReviewRequest request, DueDiligenceCase main, Instant now) {
        List<ReturnError> rows = new ArrayList<>();
        for (DecideClientReviewRequest.ReturnErrorItem item : request.getErrors()) {
            ReturnError row = new ReturnError();
            row.setReturnErrorId(support.nextId());
            row.setCaseId(main.getCaseId());
            row.setSequenceNo(rows.size() + 1);
            row.setRequirementKey(item.getRequirementKey());
            row.setSubjectPartyId(item.getSubjectPartyId());
            row.setErrorCode(item.getCode());
            row.setReason(item.getReason());
            row.setRemediationCode(item.getRemediation());
            row.setCreateTime(now);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 主案件决定对 Client 角色资格的投影
     * <li>批准：没有资格行时新建版本 1，否则切换到本次决定；拒绝：只有当前 APPROVED 的资格行才切换，否则不投影。</li>
     */
    QualificationChangeContext projection(DecideClientReviewRequest request, ClientReviewFamily family, QualificationDecision decision) {
        RoleQualification current = qualifications.findByRole(decision.getRoleType(), decision.getRoleId(),
            decision.getQualificationType());
        if (request.getAction() == ClientReviewAction.REJECT
            && (current == null || !QualificationStatus.APPROVED.getCode().equals(current.getStatus()))) {
            return null;
        }
        return QualificationChangeBuilder.change(support.ids(), decision, current, request.getReasonCode(),
            ClientReviewCommandSupport.TRIGGER_OPERATIONS, family.mainProgram().getVersion().getReviewIntervalMonths());
    }

    /** 决定人的最终资格决定，冻结案件的档次、评级、辖区、责任方与推荐人。 */
    QualificationDecision decision(DecideClientReviewRequest request, ClientReviewFamily family, DueDiligenceCase row, Instant now) {
        DueDiligenceCase main = family.getMainCase();
        QualificationDecision decision = new QualificationDecision();
        decision.setDecisionId(support.nextId());
        decision.setCaseId(row.getCaseId());
        decision.setRoleType(row.getRoleType());
        decision.setRoleId(Long.toString(row.getRoleId()));
        decision.setQualificationType(row.getQualificationType());
        decision.setDecisionSource(QualificationDecisionSource.REVIEWER.getCode());
        decision.setOutcome(request.getAction().getDecisionOutcome());
        decision.setTier(row.getTier());
        decision.setRiskRating(main.getRiskRating());
        decision.setJurisdictionCode(row.getJurisdictionCode());
        decision.setResponsiblePartnerId(row.getResponsiblePartnerId());
        decision.setRecommendedBy(main.getRecommendedBy());
        decision.setRecommendedTime(main.getRecommendedTime());
        decision.setFourEyesWaived(family.mainProgram().getVersion().requiresFourEyes() ? 0 : 1);
        decision.setOperatedBy(ReviewCodes.OPERATED_BY_PLATFORM);
        decision.setOnBehalfOfPartnerId(request.getOnBehalfOfPartnerId());
        decision.setValidFrom(now);
        decision.setDecidedBy(request.getActorId());
        decision.setDecidedTime(now);
        decision.setCreateTime(now);
        return decision;
    }

    /** 结论与资格投影共用的 READ COMMITTED、5 秒超时事务；两个 Service 加入该事务。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
        return transaction;
    }
}
