package com.paytech.platform.support.core.audit;

import java.util.List;
import java.util.Optional;

import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.*;

/** 调用终态与事务终态汇合后最多取出一次；不在事务回调提前猜测业务结果。 */
public final class AuditCompletion {
    private final Invocation invocation;
    private Identity identity = new Identity("UNKNOWN", null);
    private Target target;
    private Fact fact;
    private TransactionOutcome transaction = TransactionOutcome.NOT_STARTED;
    private CallOutcome call;
    private String safeReason;
    private boolean discarded;
    private boolean consumed;

    public AuditCompletion(Invocation invocation) {
        if (invocation == null) throw new IllegalArgumentException("审计调用元数据不能为空");
        this.invocation = invocation;
        this.target = invocation.fallback();
    }

    public synchronized void identify(Identity identity) {
        if (identity == null) throw new IllegalArgumentException("审计身份不能为空");
        if (!consumed) this.identity = identity;
    }

    public synchronized void target(Target target) {
        if (target == null || !invocation.allowedResourceTypes().contains(target.type())) {
            throw new IllegalArgumentException("审计资源类型不属于当前动作");
        }
        if (!consumed) this.target = target;
    }

    public synchronized void observeTransaction() {
        if (!consumed && transaction == TransactionOutcome.NOT_STARTED) transaction = TransactionOutcome.ACTIVE;
    }

    public synchronized void transactionCompleted(TransactionOutcome outcome) {
        if (outcome == null || outcome == TransactionOutcome.ACTIVE || outcome == TransactionOutcome.NOT_STARTED) {
            throw new IllegalArgumentException("审计事务完成通知必须为终态");
        }
        if (!consumed && transaction == TransactionOutcome.ACTIVE) transaction = outcome;
    }

    public synchronized void record(Fact fact) {
        if (consumed) return;
        if (fact == null) throw new IllegalArgumentException("审计事实不能为空");
        target(fact.target());
        identify(fact.identity());
        this.fact = fact;
    }

    public synchronized void replay() { discarded = true; }

    public synchronized void discard() { discarded = true; }

    public synchronized void callCompleted(CallOutcome outcome, String safeReason) {
        if (outcome == null) throw new IllegalArgumentException("审计调用终态不能为空");
        if (call != null || consumed) return;
        if (safeReason != null) new Attribute("reason", safeReason);
        this.call = outcome;
        this.safeReason = safeReason;
    }

    public synchronized Optional<Event> drain() {
        if (consumed || call == null || transaction == TransactionOutcome.ACTIVE) return Optional.empty();
        consumed = true;
        if (discarded) return Optional.empty();
        if (transaction == TransactionOutcome.UNKNOWN) return attempt(Result.FAILED, "AUDIT_COMMIT_OUTCOME_UNKNOWN");
        if (transaction == TransactionOutcome.COMMITTED && fact != null) {
            if (fact.result() == Result.SUCCEEDED && !invocation.recordSuccess()) return Optional.empty();
            return AuditModels.freeze(new Event(fact.identity(), invocation.action(), fact.target(), fact.result(),
                fact.attributes(), invocation.requestId(), fact.occurredAt()));
        }
        if (fact != null && fact.result() != Result.SUCCEEDED) return attempt(fact.result(), detailedReason());
        if (call == CallOutcome.DENIED) return attempt(Result.DENIED, detailedReason());
        if (call == CallOutcome.FAILED || transaction == TransactionOutcome.ROLLED_BACK) {
            return attempt(Result.FAILED, detailedReason());
        }
        return Optional.empty();
    }

    private String detailedReason() {
        if (fact != null && fact.result() != Result.SUCCEEDED) {
            for (Attribute attribute : fact.attributes()) {
                if ("reason".equals(attribute.key())) return attribute.value();
            }
        }
        if (safeReason != null) return safeReason;
        return call == CallOutcome.DENIED || fact != null && fact.result() == Result.DENIED
            ? "AUDIT_BUSINESS_DENIED" : "AUDIT_SYSTEM_FAILURE";
    }

    private Optional<Event> attempt(Result result, String reason) {
        return AuditModels.freeze(new Event(identity, invocation.action(), target, result,
            List.of(new Attribute("reason", reason)), invocation.requestId(),
            fact == null ? invocation.startedAt() : fact.occurredAt()));
    }
}
