package com.paytech.commercial.service.handler;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.commercial.service.processor.CheckCapabilityPrerequisitesProcessor;
import com.paytech.commercial.service.processor.request.CheckCapabilityPrerequisitesRequest;
import com.paytech.commercial.service.processor.response.CapabilityPrerequisiteCheckResponse;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.AggregateKey;

import lombok.AllArgsConstructor;

/**
 * 提交后执行开通项前置核对（开通项前置门禁 technique.md §11.3）
 * <li>新增开通项、恢复、重新纳入、合同激活与价格版本激活在各自本地事务内用 {@link #buildEvent(long, long)} 登记；它们自己持有合同锁且
 * 门禁需要外部查询，所以不在持锁事务中直接调用门禁，而是提交后由本 Handler 调用 {@link CheckCapabilityPrerequisitesProcessor}。</li>
 * <li>事件键 {@code AGREEMENT:{agreementId}:CapabilityPrerequisiteCheck:{changeVersion}}，不保存 Payload；同一合同版本重复登记复用同一任务。</li>
 * <li>合同不存在或为 PARTNER 合同（业务检查失败）为终态；锁冲突、版本 CAS 冲突、系统错误与前置查询不可用（资格或渠道身份结论含
 * UNKNOWN）可重试。</li>
 * <li>门禁 Processor 经 AgreementService 依赖补偿登记器，而登记器的 Handler 注册表依赖全部 Handler；为打断这一装配环，Processor 在执行时
 * 才从容器取得。</li>
 */
@AllArgsConstructor
public class CapabilityPrerequisiteCheckHandler implements CompensationEventHandler {
    /** 补偿事件类型，登记于 compensation-event-catalog.yaml。 */
    public static final String EVENT_TYPE = "AGREEMENT_CAPABILITY_PREREQUISITE_CHECK";

    /** 事件键中的业务事件段。 */
    static final String KEY_EVENT_TYPE = "CapabilityPrerequisiteCheck";

    /** 事件键聚合类型：合同。 */
    static final String AGGREGATE_TYPE = "AGREEMENT";

    private final ObjectProvider<CheckCapabilityPrerequisitesProcessor> processor;

    /**
     * 纯工厂：只能与触发核对的本地写入在同一事务登记
     *
     * @param agreementId 合同 ID
     * @param changeVersion 登记事务提交后的合同 BCR Root 版本（不推进合同版本的写入取当前版本）
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long agreementId, long changeVersion) {
        return CommercialEventKeys.request(AGGREGATE_TYPE, KEY_EVENT_TYPE, EVENT_TYPE, agreementId, changeVersion);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 20 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 12, Duration.ofSeconds(5), Duration.ofMinutes(30),
            2, 0.2);
    }

    /**
     * 调用门禁核对合同全部开通项
     * <li>必须在事务外执行；事件键非法或业务检查失败为终态，冲突、系统错误与前置查询不可用可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("PREREQUISITE_CHECK_TRANSACTION_ACTIVE",
                "Capability prerequisite check must run outside a transaction");
        }
        AggregateKey.ParsedEventKey key = CommercialEventKeys.parse(event, AGGREGATE_TYPE, KEY_EVENT_TYPE);
        if (key == null) {
            return CompensationHandlerResult.terminalFailure("PREREQUISITE_CHECK_EVENT_INVALID",
                "Capability prerequisite check event is malformed");
        }
        CapabilityPrerequisiteCheckResponse response;
        try {
            response = processor.getObject().process(new CheckCapabilityPrerequisitesRequest(
                Long.parseLong(key.aggregateKey().aggregateId())));
        } catch (ConcurrentRequestException | ErrorException retryable) {
            return CompensationHandlerResult.retryableFailure("PREREQUISITE_CHECK_RETRY",
                "Capability prerequisite check requires retry");
        } catch (FailException rejected) {
            return CompensationHandlerResult.terminalFailure("PREREQUISITE_CHECK_REJECTED",
                "Agreement is not eligible for capability prerequisite check");
        }
        if (response.isUnknown()) {
            return CompensationHandlerResult.retryableFailure("PREREQUISITE_CHECK_UNAVAILABLE",
                "Prerequisite check unavailable, retry required");
        }
        return CompensationHandlerResult.success();
    }
}
