package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.QualificationErrorCode.QUALIFICATION_NOT_FOUND;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.QualificationStateChange;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.compliance.domainservice.repository.QualificationStateChangeRepository;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.compliance.service.processor.request.QualificationHistoryRequest;
import com.paytech.compliance.service.processor.response.QualificationHistoryResponse;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 一个角色资格的状态变更与对应决定（来源、操作人、依据、原因码、时间），按聚合版本倒序；只读，不创建显式事务
 * <li>资格不存在 404 QUALIFICATION_NOT_FOUND；状态变更引用的决定缺失说明数据损坏，按系统错误处理。</li>
 */
@Component
@AllArgsConstructor
public class GetQualificationHistoryProcessor
    extends AbstractProcessor<QualificationHistoryRequest, QualificationHistoryResponse> {
    private final QualificationDecisionRepository decisions;
    private final RoleQualificationRepository qualifications;
    private final QualificationStateChangeRepository stateChanges;

    @Override
    protected QualificationHistoryResponse processRequest(QualificationHistoryRequest request) {
        if (qualifications.findById(request.getQualificationId()) == null) {
            throw new FailException(QUALIFICATION_NOT_FOUND, "角色资格不存在: " + request.getQualificationId());
        }
        List<QualificationStateChange> changes = stateChanges.findByQualification(request.getQualificationId());
        Map<Long, QualificationDecision> byId = decisions.findByIds(changes.stream().map(QualificationStateChange::getDecisionId)
            .distinct().toList()).stream().collect(Collectors.toMap(QualificationDecision::getDecisionId, Function.identity()));
        return new QualificationHistoryResponse(request.getQualificationId(),
            changes.stream().map(change -> item(change, byId.get(change.getDecisionId()))).toList());
    }

    /** 一次状态变更及引起它的决定。 */
    static QualificationHistoryResponse.Item item(QualificationStateChange change, QualificationDecision decision) {
        if (decision == null) {
            throw new ErrorException("资格状态变更引用的决定不存在: " + change.getChangeId());
        }
        return new QualificationHistoryResponse.Item(change.getChangeId(), change.getAggregateVersion(), change.getFromStatus(),
            change.getToStatus(), change.getTriggerType(), change.getReasonCode(), change.getActorId(), change.getOccurredTime(),
            decision.getDecisionId(), decision.getDecisionSource(), decision.getOutcome(), decision.getDecidedBy(),
            decision.getBasisNote(), decision.getValidFrom(), decision.getValidTo());
    }
}
