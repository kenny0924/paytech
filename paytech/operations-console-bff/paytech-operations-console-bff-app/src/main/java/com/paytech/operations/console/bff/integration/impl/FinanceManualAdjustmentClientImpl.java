package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.ManualAdjustmentApi;
import com.paytech.operations.console.bff.integration.FinanceManualAdjustmentClient;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ManualAdjustmentDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ManualAdjustmentResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ReviewManualAdjustmentRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ReviewManualAdjustmentResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.SubmitManualAdjustmentRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.SubmitManualAdjustmentResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * ledger 人工调整的 HTTP 转发：每个入口只调用一次 ledger，不重试
 * <li>命令把会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id；不转发浏览器 Authorization、Cookie 或 Idempotency-Key。</li>
 * <li>列表元素须属于请求账簿；详情与复核回执的调整主键须与请求一致。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class FinanceManualAdjustmentClientImpl implements FinanceManualAdjustmentClient {
    private final ManualAdjustmentApi api;

    /** 调整分页，核对分页回执与所属账簿。 */
    @Override
    public PagingResponseBody<ManualAdjustmentResponseBody> listManualAdjustments(String ledgerBookId,
        String accountingPeriodId, String status, String makerId, int page, int size) {
        var adjustments = page(api.listManualAdjustments(ledgerBookId, accountingPeriodId, status, makerId, page, size),
            page, size, ManualAdjustmentResponseBody.class);
        adjustments.items().forEach(item -> requireSame(ledgerBookId, item.getLedgerBookId()));
        return adjustments;
    }

    /** 调整详情；主键须与请求一致，明细与审批记录不可缺失。 */
    @Override
    public ManualAdjustmentDetailResponseBody getManualAdjustment(String manualAdjustmentId) {
        var detail = convert(api.getManualAdjustment(manualAdjustmentId), ManualAdjustmentDetailResponseBody.class);
        requireSame(manualAdjustmentId, detail.getManualAdjustmentId());
        FinanceContractConverter.require(detail.getLines() != null && detail.getApprovals() != null);
        return detail;
    }

    /** 提交调整；回执须带新主键。 */
    @Override
    public SubmitManualAdjustmentResponseBody submitManualAdjustment(OperatorContext context,
        SubmitManualAdjustmentRequestBody request) {
        var submitted = write(request, () -> api.submitManualAdjustment(context.accountId(), context.sessionId(),
            convert(request, com.paytech.ledger.api.request.SubmitManualAdjustmentRequestBody.class)).getBody(),
            SubmitManualAdjustmentResponseBody.class);
        requireIdentity(submitted.getManualAdjustmentId());
        return submitted;
    }

    /** 批准调整；回执主键须与请求一致。 */
    @Override
    public ReviewManualAdjustmentResponseBody approveManualAdjustment(OperatorContext context,
        String manualAdjustmentId, ReviewManualAdjustmentRequestBody request) {
        var reviewed = write(request, () -> api.approveManualAdjustment(context.accountId(), context.sessionId(),
            manualAdjustmentId, convert(request, com.paytech.ledger.api.request.ReviewManualAdjustmentRequestBody.class)),
            ReviewManualAdjustmentResponseBody.class);
        requireSame(manualAdjustmentId, reviewed.getManualAdjustmentId());
        return reviewed;
    }

    /** 驳回调整；回执主键须与请求一致。 */
    @Override
    public ReviewManualAdjustmentResponseBody rejectManualAdjustment(OperatorContext context,
        String manualAdjustmentId, ReviewManualAdjustmentRequestBody request) {
        var reviewed = write(request, () -> api.rejectManualAdjustment(context.accountId(), context.sessionId(),
            manualAdjustmentId, convert(request, com.paytech.ledger.api.request.ReviewManualAdjustmentRequestBody.class)),
            ReviewManualAdjustmentResponseBody.class);
        requireSame(manualAdjustmentId, reviewed.getManualAdjustmentId());
        return reviewed;
    }
}
