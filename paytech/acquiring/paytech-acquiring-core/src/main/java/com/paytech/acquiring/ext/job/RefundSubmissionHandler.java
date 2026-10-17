package com.paytech.commerce.ext.job;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.commerce.domainservice.service.refund.RefundService;
import com.paytech.commerce.integration.payment.PaymentRefundClient;
import com.paytech.commerce.service.transaction.CommerceTransactionExecutor;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

import lombok.AllArgsConstructor;

/** 退款提交与恢复入口；Payment 调用位于两个本地短事务之间，重试由 Foundation 调度。 */
@Component
@AllArgsConstructor
public class RefundSubmissionHandler implements CompensationEventHandler {

    /** 创建与恢复共享的退款提交任务类型，由执行入口统一维护。 */
    public static final String MAIN_TYPE = "SUBMISSION_EVENT_TYPE";

    private final Clock clock;
    private final RefundService refunds;
    private final PaymentRefundClient payment;
    private final CommerceTransactionExecutor transactions;

    /** 仅构造登记请求；任务凭退款号恢复，无业务子类型或重复 Payload，也不触发执行。 */
    public static CompensationEventRequest buildEvent(String no) {
        return new CompensationEventRequest(no, MAIN_TYPE, "-");
    }

    /** 返回创建端登记的稳定事件类型。 */
    @Override
    public String eventType() {
        return MAIN_TYPE;
    }

    /**
     * 从已提交退款恢复请求，事务外调用 Payment，再用短事务记录结果。 已完成退款直接成功；未决结果和调用异常保留预占，由 Foundation 重试同一任务。
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (!eventType().equals(event.eventType()) || event.payloadJson() != null) {
            return CompensationHandlerResult.terminalFailure("INVALID_REFUND_TASK", "退款任务元数据不符合约定");
        }
        // 锁内重建稳定提交请求；终态不再调用 Payment。
        var request = prepareSubmission(event.eventKey());
        if (request == null) {
            return CompensationHandlerResult.success();
        }
        // 外部调用不持有本地业务事务。
        var result = payment.submitOrQuery(request);
        // 独立短事务绑定结果，并消费或释放预占。
        return completeSubmission(request, result);
    }

    /** 事务内将首次请求推进至处理中；已完成退款返回 null。 */
    PaymentRefundClient.Request prepareSubmission(String refundRequestNo) {
        return transactions
            .write(() -> refunds.prepare(refundRequestNo, clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    /** 原子提交 Payment 结果；未决退款保持可重试，确定终态才完成任务。 */
    CompensationHandlerResult completeSubmission(PaymentRefundClient.Request request,
        PaymentRefundClient.Result result) {
        boolean completed =
            transactions.write(() -> refunds.complete(request, result, clock.instant().truncatedTo(ChronoUnit.MICROS)));
        return completed ? CompensationHandlerResult.success()
            : CompensationHandlerResult.retryableFailure("REFUND_PROCESSING", "Payment 退款结果尚未确定");
    }
}
