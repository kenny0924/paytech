package com.paytech.payout.domainservice.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryEventAction;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccountEvent;
import com.paytech.payout.domain.BeneficiaryCommandContext;
import com.paytech.payout.domain.BeneficiaryNotificationSource;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountEventRepository;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.service.handler.BeneficiaryNotificationHandler;

import lombok.AllArgsConstructor;

/** 收款账户本地保存；每个方法一个 TransactionTemplate 事务，事件与通知登记随状态一起提交或回滚。 */
@Service
@AllArgsConstructor
public class BeneficiaryAccountServiceImpl implements BeneficiaryAccountService {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final BeneficiaryAccountRepository beneficiaryAccountRepository;
    private final BeneficiaryAccountEventRepository beneficiaryAccountEventRepository;

    /** 插入账户与首条事件。 */
    @Override
    public void submit(BeneficiaryAccount account, String sourceKey) {
        transactionTemplate.executeWithoutResult(status -> {
            beneficiaryAccountRepository.insert(account);
            appendEvent(new BeneficiaryCommandContext(account, account.getSubmittedByType(), account.getSubmittedBy(), null,
                sourceKey), null, BeneficiaryEventAction.SUBMIT, account.getCreateTime());
        });
    }

    /** 记录 Maker 意见。 */
    @Override
    public void recordMakerReview(BeneficiaryCommandContext context, MakerReviewDecision decision, String makerNote,
        String reasonNote) {
        BeneficiaryAccount account = context.getAccount();
        transactionTemplate.executeWithoutResult(status -> {
            beneficiaryAccountRepository.recordMakerReview(account, context.getActorId(), decision, context.getReasonCode(),
                makerNote, reasonNote);
            appendEvent(context, account.getStatus(), BeneficiaryEventAction.MAKER_REVIEW, account.getUpdateTime());
        });
    }

    /** 记录 Checker 决定并登记核验结果通知。 */
    @Override
    public void recordCheckerDecision(BeneficiaryCommandContext context, CheckerDecision decision, String reasonNote,
        Duration cooling) {
        BeneficiaryAccount account = context.getAccount();
        BeneficiaryAccountStatus from = account.getStatus();
        transactionTemplate.executeWithoutResult(status -> {
            beneficiaryAccountRepository.recordCheckerDecision(account, context.getActorId(), decision,
                context.getReasonCode(), reasonNote, cooling);
            BeneficiaryAccountEvent event = appendEvent(context, from, BeneficiaryEventAction.CHECKER_DECIDE,
                account.getUpdateTime());
            registerNotification(account, event);
        });
    }

    /** 切换默认账户。 */
    @Override
    public void setDefault(BeneficiaryCommandContext context, BeneficiaryAccount previousDefault) {
        BeneficiaryAccount account = context.getAccount();
        transactionTemplate.executeWithoutResult(status -> {
            if (previousDefault != null) {
                beneficiaryAccountRepository.updateDefault(previousDefault, false);
                appendEvent(new BeneficiaryCommandContext(previousDefault, context.getActorType(), context.getActorId(), null,
                    context.getSourceKey()), previousDefault.getStatus(), BeneficiaryEventAction.UNSET_DEFAULT,
                    previousDefault.getUpdateTime());
            }
            beneficiaryAccountRepository.updateDefault(account, true);
            appendEvent(context, account.getStatus(), BeneficiaryEventAction.SET_DEFAULT, account.getUpdateTime());
        });
    }

    /** 停用并登记通知。 */
    @Override
    public void suspend(BeneficiaryCommandContext context) {
        changeStatus(context, BeneficiaryAccountStatus.SUSPENDED, BeneficiaryEventAction.SUSPEND);
    }

    /** 关闭并登记通知。 */
    @Override
    public void close(BeneficiaryCommandContext context) {
        changeStatus(context, BeneficiaryAccountStatus.CLOSED, BeneficiaryEventAction.CLOSE);
    }

    /** 恢复重新核验。 */
    @Override
    public void resume(BeneficiaryCommandContext context) {
        BeneficiaryAccount account = context.getAccount();
        BeneficiaryAccountStatus from = account.getStatus();
        transactionTemplate.executeWithoutResult(status -> {
            beneficiaryAccountRepository.resume(account);
            appendEvent(context, from, BeneficiaryEventAction.RESUME, account.getUpdateTime());
        });
    }

    /** 追加 REVEAL 事件。 */
    @Override
    public void recordReveal(BeneficiaryCommandContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        transactionTemplate.executeWithoutResult(status -> appendEvent(context, context.getAccount().getStatus(),
            BeneficiaryEventAction.REVEAL, now));
    }

    /** 读取账户与触发事件，不开事务。 */
    @Override
    public BeneficiaryNotificationSource loadNotificationSource(Long beneficiaryAccountId, Integer eventNo) {
        BeneficiaryAccount account = beneficiaryAccountRepository.findById(beneficiaryAccountId);
        BeneficiaryAccountEvent event = beneficiaryAccountEventRepository.findByEventNo(beneficiaryAccountId, eventNo);
        return account == null || event == null ? null : new BeneficiaryNotificationSource(account, event);
    }

    /** 停用或关闭：迁移状态、追加事件、登记通知。 */
    void changeStatus(BeneficiaryCommandContext context, BeneficiaryAccountStatus target, BeneficiaryEventAction action) {
        BeneficiaryAccount account = context.getAccount();
        BeneficiaryAccountStatus from = account.getStatus();
        transactionTemplate.executeWithoutResult(status -> {
            beneficiaryAccountRepository.updateStatusWithReason(account, target, context.getReasonCode());
            registerNotification(account, appendEvent(context, from, action, account.getUpdateTime()));
        });
    }

    /**
     * 追加一条事件：序号为账户当前最大序号 + 1，账户版本取本事务内更新后的版本
     *
     * @param context 账户、操作人、原因与来源键
     * @param from 变更前状态
     * @param action 动作
     * @param occurredTime 发生时间（与账户更新时间一致）
     * @return 已插入的事件
     */
    BeneficiaryAccountEvent appendEvent(BeneficiaryCommandContext context, BeneficiaryAccountStatus from,
        BeneficiaryEventAction action, Instant occurredTime) {
        BeneficiaryAccount account = context.getAccount();
        BeneficiaryAccountEvent event = new BeneficiaryAccountEvent();
        event.setId(idGenerator.nextId());
        event.setBeneficiaryAccountId(account.getId());
        event.setEventNo(beneficiaryAccountEventRepository.nextEventNo(account.getId()));
        event.setFromStatus(from);
        event.setToStatus(account.getStatus());
        event.setAction(action);
        event.setReasonCode(context.getReasonCode());
        event.setActorType(context.getActorType());
        event.setActorId(context.getActorId());
        event.setSourceKey(context.getSourceKey());
        event.setAccountVersionAfter(account.getVersion());
        event.setOccurredTime(occurredTime);
        event.setCreateTime(occurredTime);
        beneficiaryAccountEventRepository.insert(event);
        return event;
    }

    /** 登记账户当前状态对应的通知；状态没有模板时不登记。 */
    void registerNotification(BeneficiaryAccount account, BeneficiaryAccountEvent event) {
        String template = BeneficiaryNotificationHandler.templateFor(account.getStatus());
        if (template != null) {
            compensationEventRegistrar.register(BeneficiaryNotificationHandler.buildEvent(account.getId(), event.getEventNo(),
                template));
        }
    }
}
