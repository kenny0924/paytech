package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.*;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.domain.ClientReviewDecisionContext;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domainservice.policy.ClientReviewApprovalPolicy;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.DecideClientReviewRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;

import lombok.AllArgsConstructor;

/**
 * O07 退回、O08 通过、O09 最终拒绝：结束本次提交的主任务与仍在进行的子任务，并登记结果发布
 * <li>通过前由 ClientReviewApprovalPolicy 判断门禁；页面勾选不能代替。退回和拒绝不要求门禁。</li>
 * <li>任务已以同一结论结束直接返回当前结果；已以其他结论结束 409。</li>
 * <li>主任务按读取门禁时的版本条件结束，期间有其他审核命令时整体回滚并返回 409。</li>
 * <li>内部理由只写 resolution_note，不自动复制为客户可见说明。</li>
 */
@Component
@AllArgsConstructor
public class DecideClientReviewProcessor extends AbstractProcessor<DecideClientReviewRequest, ClientReviewCommandResponse> {
    /** 运营审核命令的触发类型。 */
    static final String TRIGGER_TYPE = "OPERATIONS_REVIEW";

    private final Clock clock;
    private final ClientReviewService reviews;
    private final ComplianceIdentifierGenerator ids;
    private final ClientReviewFamilyLoader families;

    /** 退回要求分区和客户可见说明；通过与拒绝要求内部理由且不能带分区。 */
    @Override
    protected void validate(DecideClientReviewRequest request) {
        if (request.getAction() == ClientReviewAction.RETURN) {
            if (request.getReturnSection() == null || request.getPublicNote() == null || request.getPublicNote().isBlank()) {
                throw new BizCheckFailException("退回必须选择分区并填写客户可见说明");
            }
            return;
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BizCheckFailException("最终决定必须填写审核理由");
        }
        if (request.getReturnSection() != null) {
            throw new BizCheckFailException("只有退回可以指定分区");
        }
    }

    @Override
    protected ClientReviewCommandResponse processRequest(DecideClientReviewRequest request) {
        // 当前状态与门禁
        ClientReviewFamily family = families.load(request.getCaseId());
        DueDiligenceCase main = family.getMainCase();
        if (!family.isActive()) {
            if (request.getAction().getCaseStatus().equals(main.getStatus())) {
                return new ClientReviewCommandResponse(main.getCaseId(), main.getStatus());
            }
            throw new FailException(CLIENT_REVIEW_CLOSED);
        }
        if (request.getAction() == ClientReviewAction.APPROVE) {
            List<String> blockers = ClientReviewApprovalPolicy.blockers(family);
            if (!blockers.isEmpty()) {
                throw new FailException(CLIENT_REVIEW_NOT_READY, "审核尚未满足通过条件: " + String.join(",", blockers));
            }
        }

        // 构建并保存结论
        reviews.decide(buildContext(request, family, clock.instant().truncatedTo(ChronoUnit.MICROS)));
        return new ClientReviewCommandResponse(main.getCaseId(), main.getStatus());
    }

    /** 一次构造全部要结束的任务、决定与状态变更。 */
    ClientReviewDecisionContext buildContext(DecideClientReviewRequest request, ClientReviewFamily family, Instant now) {
        ClientReviewAction action = request.getAction();
        DueDiligenceCase main = family.getMainCase();
        List<DueDiligenceCase> children = family.getChildCases().stream()
            .filter(child -> Integer.valueOf(1).equals(child.getActiveSlot())).toList();
        List<QualificationDecision> decisions = new ArrayList<>();
        List<CaseStateChange> changes = new ArrayList<>();
        changes.add(stateChange(request, main, main, action.getCaseStatus(), action.name(), now));
        if (action.getDecisionOutcome() != null) {
            decisions.add(decision(request, main, action.getDecisionOutcome(), now));
        }
        for (DueDiligenceCase child : children) {
            String childAction = action == ClientReviewAction.REJECT ? "WITHDRAW_WITH_PARENT" : action.name();
            changes.add(stateChange(request, main, child, action.getChildStatus(), childAction, now));
            if (action == ClientReviewAction.APPROVE) {
                decisions.add(decision(request, child, action.getDecisionOutcome(), now));
            }
        }
        String reason = action == ClientReviewAction.RETURN ? null : request.getReason();
        return new ClientReviewDecisionContext(main, children, action, request.getPublicNote(), reason,
            request.getReturnSection(), decisions, changes, now);
    }

    /** 审核人的最终资格决定。 */
    QualificationDecision decision(DecideClientReviewRequest request, DueDiligenceCase row, String outcome, Instant now) {
        QualificationDecision decision = new QualificationDecision();
        decision.setDecisionId(ids.nextIdentifier());
        decision.setCaseId(row.getCaseId());
        decision.setRoleType(row.getRoleType());
        decision.setRoleId(row.getRoleId());
        decision.setQualificationType(row.getQualificationType());
        decision.setDecisionSource("REVIEWER");
        decision.setOutcome(outcome);
        decision.setValidFrom(now);
        decision.setDecidedBy(request.getActorId());
        decision.setDecidedTime(now);
        decision.setCreateTime(now);
        return decision;
    }

    /** 结束任务的状态变更；聚合版本为结束后的版本，触发标识为主任务与结论。 */
    CaseStateChange stateChange(DecideClientReviewRequest request, DueDiligenceCase main, DueDiligenceCase row, String toStatus,
        String actionType, Instant now) {
        CaseStateChange change = new CaseStateChange();
        change.setChangeId(ids.nextIdentifier());
        change.setCaseId(row.getCaseId());
        change.setFromStatus(row.getStatus());
        change.setToStatus(toStatus);
        change.setActionType(actionType);
        change.setReasonCode(request.getReasonCode());
        change.setTriggerType(TRIGGER_TYPE);
        change.setTriggerId("CLIENT_REVIEW:" + main.getCaseId() + ":" + request.getAction().name());
        change.setActorId(request.getActorId());
        change.setAggregateVersion(row.getVersion() + 1);
        change.setOccurredTime(now);
        change.setCreateTime(now);
        return change;
    }
}
