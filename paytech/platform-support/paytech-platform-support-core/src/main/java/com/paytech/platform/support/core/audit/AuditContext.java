package com.paytech.platform.support.core.audit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.*;

import io.micrometer.core.instrument.MeterRegistry;

/** 线程作用域只持有冻结值；事务回调直接持有完成状态，不重新读取请求线程。 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${operations.authentication.enabled:false} || ${client.authentication.enabled:false}")
public final class AuditContext {
    private final AuditDispatcher dispatcher;
    private final ThreadLocal<Scope> current = new ThreadLocal<>();
    private final AtomicInteger active = new AtomicInteger();

    public AuditContext(MeterRegistry registry, AuditDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        try { registry.gauge("platform.audit.active.scopes", active); }
        catch (RuntimeException ignored) { /* 指标不决定业务可用性。 */ }
    }

    public Scope open(Invocation invocation) {
        Scope previous = current.get();
        try {
            State state = previous != null && previous.state != null && sameInvocation(previous.state.invocation, invocation)
                ? previous.state : new State(invocation);
            boolean owner = previous == null || previous.state != state;
            Scope scope = new Scope(previous, state, owner);
            if (owner) {
                active.incrementAndGet();
            }
            current.set(scope);
            observeTransaction();
            return scope;
        } catch (RuntimeException ignored) {
            dispatcher.drop("capture");
            Scope scope = new Scope(previous, null, false);
            current.set(scope);
            return scope;
        }
    }

    public void observeTransaction() {
        mutate(state -> {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) return;
            if (state.synchronization != null) {
                if (!TransactionSynchronizationManager.getSynchronizations().contains(state.synchronization)) {
                    throw new IllegalStateException("审计调用不能混合不同所属事务");
                }
                return;
            }
            state.completion.observeTransaction();
            state.waiting = true;
            state.synchronization = new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    synchronized (state) {
                        try {
                            state.completion.transactionCompleted(switch (status) {
                                case STATUS_COMMITTED -> TransactionOutcome.COMMITTED;
                                case STATUS_ROLLED_BACK -> TransactionOutcome.ROLLED_BACK;
                                default -> TransactionOutcome.UNKNOWN;
                            });
                            state.waiting = false;
                            drain(state);
                        } catch (RuntimeException ignored) { fail(state, "capture"); }
                    }
                }
            };
            TransactionSynchronizationManager.registerSynchronization(state.synchronization);
        });
    }

    public void identify(Identity identity) { mutate(state -> state.completion.identify(identity)); }

    /** 立即执行审计专用值构造；没有作用域时不求值，也不保存闭包。 */
    public void capture(Runnable auditCapture) { mutate(state -> auditCapture.run()); }

    public void target(Target target) { mutate(state -> state.completion.target(target)); }

    public void record(Fact fact) {
        mutate(state -> state.completion.record(AuditModels.freeze(fact).orElseThrow()));
    }

    public void replay() { mutate(state -> { state.completion.replay(); settle(state); }); }

    public void complete(CallOutcome outcome, String safeReason) {
        Scope scope = current.get();
        if (scope == null || !scope.owner) return;
        mutate(state -> {
            state.completion.callCompleted(outcome, safeReason);
            state.completed = true;
            drain(state);
        });
    }

    public void discard() { mutate(state -> fail(state, "capture")); }

    public int activeScopeCount() { return active.get(); }

    private boolean sameInvocation(Invocation first, Invocation second) {
        return second != null && first.action().equals(second.action()) && first.requestId().equals(second.requestId());
    }

    private void mutate(Consumer<State> action) {
        Scope scope = current.get();
        if (scope == null || scope.state == null) return;
        State state = scope.state;
        synchronized (state) {
            if (state.settled) return;
            try { action.accept(state); }
            catch (IllegalArgumentException ignored) { fail(state, "oversize"); }
            catch (RuntimeException ignored) { fail(state, "capture"); }
        }
    }

    private void drain(State state) {
        if (state.settled || !state.completed || state.waiting) return;
        try {
            state.completion.drain().ifPresent(dispatcher::offer);
        } catch (IllegalArgumentException ignored) {
            state.completion.discard();
            dispatcher.drop("oversize");
        } catch (RuntimeException ignored) {
            state.completion.discard();
            dispatcher.drop("capture");
        } finally { settle(state); }
    }

    private void fail(State state, String reason) {
        if (state.settled) return;
        state.completion.discard();
        dispatcher.drop(reason);
        settle(state);
    }

    private void settle(State state) {
        state.settled = true;
        account(state);
    }

    private void account(State state) {
        if (state.settled && state.ownerClosed && !state.accounted) {
            state.accounted = true;
            active.decrementAndGet();
        }
    }

    public final class Scope implements AutoCloseable {
        private final Scope previous;
        private final State state;
        private final boolean owner;
        private boolean closed;

        private Scope(Scope previous, State state, boolean owner) {
            this.previous = previous;
            this.state = state;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                if (owner && state != null) {
                    synchronized (state) {
                        if (!state.completed && !state.settled) fail(state, "capture");
                        state.ownerClosed = true;
                        account(state);
                    }
                }
            } catch (RuntimeException ignored) { dispatcher.drop("capture"); }
            finally {
                if (previous == null) current.remove();
                else current.set(previous);
            }
        }
    }

    private static final class State {
        private final Invocation invocation;
        private final AuditCompletion completion;
        private TransactionSynchronization synchronization;
        private boolean waiting;
        private boolean completed;
        private boolean settled;
        private boolean ownerClosed;
        private boolean accounted;

        private State(Invocation invocation) {
            this.invocation = invocation;
            completion = new AuditCompletion(invocation);
        }
    }
}
