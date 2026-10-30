package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierApprovalLevel;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;
import com.paytech.risk.control.core.enums.LimitTierSource;
import com.paytech.risk.control.domain.limittier.ClientTierChangeView;
import com.paytech.risk.control.domain.limittier.LimitTierException;
import com.paytech.risk.control.domain.limittier.LimitTierException.Reason;
import com.paytech.risk.control.domain.limittier.TierObservation;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.port.LimitTierUsageReader;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierChangeRepository;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierRepository;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.service.ClientLimitTierService;
import com.paytech.risk.control.service.processor.request.RequestClientTierChangeRequest;

import lombok.AllArgsConstructor;

/**
 * 提交档位变更申请（L4）
 * <li>只允许 B3 列出的迁移；同一 Client 至多一个待审批申请（待审批槽位唯一兜底）。</li>
 * <li>VERIFICATION → OBSERVATION 为上线复核：清单系统项（资格 APPROVED、本库有有效承保、有 ENABLED 开通项）与四个人工项全部满足，且写上线说明。</li>
 * <li>审批级别按本库有效承保的 CRR 冻结：LOW 为 STANDARD，其余（含无承保或未评）为 ELEVATED；当前为 OBSERVATION 时冻结已过天数与放行笔数。</li>
 */
@Component
@AllArgsConstructor
public class RequestClientTierChangeProcessor
    extends AbstractLockProcessor<RequestClientTierChangeRequest, ClientTierChangeView> {
    private final Clock clock;
    private final LimitTierUsageReader usage;
    private final ClientLimitTierService service;
    private final ClientLimitTierRepository tiers;
    private final ControlIdentifierGenerator identifiers;
    private final ClientUnderwritingRepository underwritings;
    private final ClientLimitTierChangeRepository changes;

    /** 在等待时间内尝试获取 Client 档位锁。 */
    @Override
    protected LockCommand lockCommand(RequestClientTierChangeRequest request) {
        return LockCommand.tryLocks(List.of(LimitTierCommands.client(request.getClientId())), LimitTierCommands.WAIT_TIME,
            LimitTierCommands.LEASE_TIME);
    }

    @Override
    protected ClientTierChangeView processWithinLock(RequestClientTierChangeRequest request) {
        // 业务验证
        ClientLimitTier tier = LimitTierCommands.requireTier(tiers, request.getClientId());
        requireTierVersion(tier, request.getExpectedTierVersion());
        requireNoPending(request.getClientId());
        requireAllowedTransition(tier, request.getToTier());
        ClientUnderwriting effective = underwritings.findEffective(request.getClientId()).orElse(null);
        if (isGoLiveReview(tier, request)) {
            requireGoLiveChecklist(request, effective);
        }

        // 保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ClientLimitTierChange change = buildChange(request, tier, effective, now);
        service.requestChange(change);
        return ClientTierChangeView.of(change);
    }

    /** 调用方看到的档位版本须是当前版本。 */
    static void requireTierVersion(ClientLimitTier tier, int expectedVersion) {
        if (tier.getVersion() != expectedVersion) {
            throw new LimitTierException(Reason.VERSION_CONFLICT, "Client " + tier.getClientId() + " 的档位已变更，当前版本 "
                + tier.getVersion() + "，期望 " + expectedVersion);
        }
    }

    /** 同一 Client 不能已有待审批申请。 */
    void requireNoPending(long clientId) {
        changes.findPending(clientId).ifPresent(pending -> {
            throw new LimitTierException(Reason.PENDING_EXISTS,
                "Client " + clientId + " 已有待审批的档位变更申请: " + pending.getChangeId());
        });
    }

    /** 目标档位须在允许的迁移内。 */
    static void requireAllowedTransition(ClientLimitTier tier, LimitTier toTier) {
        if (!tier.getTier().allowsRequestTo(toTier, tier.getPreviousTier())) {
            throw new LimitTierException(Reason.TRANSITION_INVALID, "Client " + tier.getClientId() + " 不能从 "
                + tier.getTier().getCode() + " 申请变更为 " + toTier.getCode());
        }
    }

    /** VERIFICATION → OBSERVATION 是上线复核。 */
    static boolean isGoLiveReview(ClientLimitTier tier, RequestClientTierChangeRequest request) {
        return tier.getTier() == LimitTier.VERIFICATION && request.getToTier() == LimitTier.OBSERVATION;
    }

    /** 上线复核须有上线说明与全部满足的清单；承保以本库有效结论为准。 */
    static void requireGoLiveChecklist(RequestClientTierChangeRequest request, ClientUnderwriting effective) {
        if (request.getGoLiveNote() == null || request.getGoLiveNote().isBlank()) {
            throw new LimitTierException(Reason.CHECKLIST_NOT_SATISFIED,
                "Client " + request.getClientId() + " 上线复核须写上线说明");
        }
        if (request.getChecklist() == null || !request.getChecklist().satisfied(effective != null)) {
            throw new LimitTierException(Reason.CHECKLIST_NOT_SATISFIED, "Client " + request.getClientId()
                + " 上线复核清单未满足：须资格已批准、有有效承保、已有开通的支付方式，且四个人工项全部确认");
        }
    }

    /** 构造待审批申请：冻结 CRR、审批级别、观察目标、申请时观察期进度与复核清单。 */
    ClientLimitTierChange buildChange(RequestClientTierChangeRequest request, ClientLimitTier tier,
        ClientUnderwriting effective, Instant now) {
        ClientLimitTierChange change = new ClientLimitTierChange();
        change.setChangeId(identifiers.nextIdentifier());
        change.setClientId(request.getClientId());
        change.setChangeType(LimitTierSource.REQUEST);
        change.setFromTier(tier.getTier());
        change.setToTier(request.getToTier());
        change.setStatus(LimitTierChangeStatus.PENDING);
        change.setPendingSlot(Boolean.TRUE);
        change.setCrrSnapshot(effective == null ? null : effective.getCrr());
        change.setApprovalLevel(LimitTierApprovalLevel.of(change.getCrrSnapshot()));
        change.setUnderwritingId(effective == null ? null : effective.getUnderwritingId());
        change.setReason(request.getReason());
        change.setGoLiveNote(blankToNull(request.getGoLiveNote()));
        if (isGoLiveReview(tier, request)) {
            request.getChecklist().applyTo(change);
        }
        applyObservationTargets(change, request, tier, effective);
        applyObservationProgress(change, tier, now);
        change.setRequestedBy(request.getActorAccountId());
        change.setRequestedTime(now);
        change.setVersion(1);
        change.setCreateTime(now);
        change.setUpdateTime(now);
        return change;
    }

    /** 目标为 OBSERVATION 时写观察目标：请求值优先，延长沿用当前目标，否则取有效承保，无承保为默认 30 天 / 100 笔。 */
    static void applyObservationTargets(ClientLimitTierChange change, RequestClientTierChangeRequest request,
        ClientLimitTier tier, ClientUnderwriting effective) {
        if (request.getToTier() != LimitTier.OBSERVATION) {
            return;
        }
        boolean extension = tier.getTier() == LimitTier.OBSERVATION;
        Integer days = extension ? tier.getObservationTargetDays()
            : effective == null ? null : effective.getObservationDays();
        Integer transactions = extension ? tier.getObservationTargetTransactions()
            : effective == null ? null : effective.getObservationTransactions();
        change.setObservationTargetDays(firstNonNull(request.getObservationTargetDays(), days,
            TierObservation.DEFAULT_TARGET_DAYS));
        change.setObservationTargetTransactions(firstNonNull(request.getObservationTargetTransactions(), transactions,
            TierObservation.DEFAULT_TARGET_TRANSACTIONS));
    }

    /** 当前为 OBSERVATION 时冻结申请时的已过天数与放行笔数，供审批人对照。 */
    void applyObservationProgress(ClientLimitTierChange change, ClientLimitTier tier, Instant now) {
        if (!TierObservation.inObservation(tier)) {
            return;
        }
        change.setObservationDaysAtRequest(TierObservation.elapsedDays(tier.getObservationStartedTime(), now));
        change.setObservationAllowedAtRequest(
            usage.countAllowedPaymentAttempts(tier.getClientId(), tier.getObservationStartedTime(), now));
    }

    /** 依次取第一个非空值。 */
    static int firstNonNull(Integer first, Integer second, int fallback) {
        if (first != null) {
            return first;
        }
        return second != null ? second : fallback;
    }

    /** 空白文本按未填写处理。 */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
