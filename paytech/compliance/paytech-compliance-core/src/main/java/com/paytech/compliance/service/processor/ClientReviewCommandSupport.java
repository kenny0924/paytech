package com.paytech.compliance.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.enums.ClientReviewErrorCode;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ReviewCommandContext;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.foundation.common.error.FailException;

/**
 * 运营审核命令的公共构造：确认主案件仍在进行、准备 SUBMITTED → IN_REVIEW 的状态变更与案件状态变更行
 * <li>纯内存构造（时间与 ID 生成除外），不访问数据库。</li>
 */
@Component
class ClientReviewCommandSupport {
    /** 运营审核命令的触发类型。 */
    static final String TRIGGER_OPERATIONS = "OPERATIONS_REVIEW";

    private final Clock clock;
    private final ComplianceIdentifierGenerator ids;

    /** 注入时间与标识生成。 */
    ClientReviewCommandSupport(Clock clock, ComplianceIdentifierGenerator ids) {
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * 构造审核命令的公共事实
     *
     * @param family 审核全貌
     * @param actorId 操作人
     * @param onBehalfOfPartnerId 代操作的责任 PayFac
     * @return 命令上下文；主案件已结束时 409 CLIENT_REVIEW_CLOSED
     */
    ReviewCommandContext begin(ClientReviewFamily family, long actorId, Long onBehalfOfPartnerId) {
        DueDiligenceCase main = family.getMainCase();
        if (!family.isActive()) {
            throw new FailException(ClientReviewErrorCode.CLIENT_REVIEW_CLOSED,
                "审核案件已结束，不能再修改: " + main.getCaseId() + ", 状态: " + main.getStatus());
        }
        Instant now = now();
        CaseStateChange start = DueDiligenceCaseStatus.SUBMITTED.getCode().equals(main.getStatus())
            ? stateChange(main, DueDiligenceCaseStatus.IN_REVIEW.getCode(), "START_REVIEW", null, actorId, onBehalfOfPartnerId, now)
            : null;
        return new ReviewCommandContext(main, actorId, onBehalfOfPartnerId, start, now);
    }

    /**
     * 案件状态变更行；聚合版本为迁移后的案件版本
     *
     * @param row 迁移前的案件
     * @param toStatus 新状态
     * @param actionType 动作
     * @param reasonCode 原因码
     * @param actorId 操作人；系统为空
     * @param onBehalfOfPartnerId 代操作的责任 PayFac
     * @param now 发生时间
     * @return 状态变更
     */
    CaseStateChange stateChange(DueDiligenceCase row, String toStatus, String actionType, String reasonCode, Long actorId,
        Long onBehalfOfPartnerId, Instant now) {
        CaseStateChange change = new CaseStateChange();
        change.setChangeId(ids.nextIdentifier());
        change.setCaseId(row.getCaseId());
        change.setFromStatus(row.getStatus());
        change.setToStatus(toStatus);
        change.setActionType(actionType);
        change.setReasonCode(reasonCode);
        change.setTriggerType(TRIGGER_OPERATIONS);
        change.setTriggerId(actorId == null ? "SYSTEM" : Long.toString(actorId));
        change.setActorId(actorId);
        change.setOperatedBy(actorId == null ? null : ReviewCodes.OPERATED_BY_PLATFORM);
        change.setOnBehalfOfPartnerId(onBehalfOfPartnerId);
        change.setAggregateVersion(row.getVersion() + 1);
        change.setOccurredTime(now);
        change.setCreateTime(now);
        return change;
    }

    /** 标识生成器（供纯构造协作者使用）。 */
    ComplianceIdentifierGenerator ids() {
        return ids;
    }

    /** 下一个标识。 */
    long nextId() {
        return ids.nextIdentifier();
    }

    /** 微秒精度的当前时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
