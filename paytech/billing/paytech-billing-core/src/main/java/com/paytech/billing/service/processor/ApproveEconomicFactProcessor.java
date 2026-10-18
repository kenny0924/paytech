package com.paytech.billing.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.core.constant.BillingLocks;
import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.domain.EconomicFactContext;
import com.paytech.billing.domain.view.EconomicFactView;
import com.paytech.billing.domainservice.repository.EconomicFactRepository;
import com.paytech.billing.domainservice.service.EconomicFactService;
import com.paytech.billing.service.processor.request.ApproveEconomicFactRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * Checker 批准经济事实（Maker-Checker，design/04 §2）
 * <li>已批准（含随后被替代）的事实：同一复核人重复批准返回原事实，不重复登记事件；其他人拒绝。</li>
 * <li>DRAFT：复核人必须不同于编制人；有被替代事实时它必须仍为 APPROVED，新事实版本 = 被替代事实版本 + 1，否则为 1。</li>
 * <li>同一本地事务：事实 DRAFT → APPROVED 并定稿版本、被替代事实 APPROVED → SUPERSEDED、登记 EconomicFactRecorded 发布任务；
 * 发布由补偿 Handler 在提交后执行，本入口事务内不调用外部服务。</li>
 * <li>同一事实的批准共用资源锁并持续等待，并发批准只有一次进入 DRAFT 分支；两条更正事实替代同一事实时由被替代事实的状态与版本条件更新保护。</li>
 */
@Component
@AllArgsConstructor
public class ApproveEconomicFactProcessor extends AbstractLockProcessor<ApproveEconomicFactRequest, EconomicFactView> {
    private final Clock clock;
    private final EconomicFactService economicFactService;
    private final EconomicFactRepository economicFactRepository;

    /** 事实资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ApproveEconomicFactRequest request) {
        return LockCommand.lock(BillingLocks.economicFact(request.getFactId()));
    }

    @Override
    protected EconomicFactView processWithinLock(ApproveEconomicFactRequest request) {
        // 查询原批准
        EconomicFactContext loaded = getAndCheckFact(request.getFactId());
        if (loaded.recorded()) {
            return getOriginalApproval(loaded, request.getCheckerId());
        }

        // 构建上下文
        EconomicFactContext context = buildApproveContext(loaded, request.getCheckerId());

        // 保存上下文
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        economicFactService.approve(context, request.getCheckerId(), now);

        // 响应处理
        return context.toView();
    }

    /** 读取事实；不存在时业务拒绝。 */
    EconomicFactContext getAndCheckFact(long factId) {
        EconomicFactContext context = economicFactService.loadContext(factId);
        if (context == null) {
            throw new BizCheckFailException("经济事实不存在: " + factId);
        }
        return context;
    }

    /** 已批准事实只对原复核人返回原结果。 */
    EconomicFactView getOriginalApproval(EconomicFactContext context, long checkerId) {
        EconomicFact fact = context.getFact();
        if (!Objects.equals(fact.getCheckerId(), checkerId)) {
            throw new BizCheckFailException("经济事实已由其他复核人批准: " + fact.getId() + ", 状态 " + fact.getStatus().getCode());
        }
        return context.toView();
    }

    /** 复核人不同于编制人；有被替代事实时加载并要求其仍为 APPROVED。 */
    EconomicFactContext buildApproveContext(EconomicFactContext loaded, long checkerId) {
        EconomicFact fact = loaded.getFact();
        if (Objects.equals(fact.getMakerId(), checkerId)) {
            throw new BizCheckFailException("复核人不能是编制人: 经济事实 " + fact.getId() + ", 人员 " + checkerId);
        }
        EconomicFact superseded = null;
        if (fact.getSupersedesFactId() != null) {
            superseded = economicFactRepository.findById(fact.getSupersedesFactId());
            if (superseded == null || superseded.getStatus() != EconomicFactStatus.APPROVED) {
                throw new BizCheckFailException("被替代事实已不是已批准状态: " + fact.getSupersedesFactId() + ", 经济事实 "
                    + fact.getId());
            }
        }
        return new EconomicFactContext(fact, loaded.getLines(), superseded, loaded.getMinorUnit());
    }
}
