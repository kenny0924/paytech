package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ManualAdjustmentDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ManualAdjustmentResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ReviewManualAdjustmentRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ReviewManualAdjustmentResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.SubmitManualAdjustmentRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.SubmitManualAdjustmentResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * ledger 人工调整的本地端口；调整内容、摘要、复核规则与过账归 ledger
 * <li>命令以会话 Account ID 与 Session ID 作为操作人 Header 转发；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface FinanceManualAdjustmentClient {

    /**
     * 账簿的人工调整分页，按提交时间倒序
     *
     * @param ledgerBookId 账簿主键
     * @param accountingPeriodId 可选会计期间主键
     * @param status 可选状态
     * @param makerId 可选编制人
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 调整摘要分页
     */
    PagingResponseBody<ManualAdjustmentResponseBody> listManualAdjustments(String ledgerBookId,
        String accountingPeriodId, String status, String makerId, int page, int size);

    /**
     * 人工调整详情
     *
     * @param manualAdjustmentId 调整主键
     * @return 调整头、明细、审批记录与过账结果
     */
    ManualAdjustmentDetailResponseBody getManualAdjustment(String manualAdjustmentId);

    /**
     * 创建并提交人工调整
     *
     * @param context 编制人会话
     * @param request 调整内容
     * @return 调整主键、单号与内容摘要
     */
    SubmitManualAdjustmentResponseBody submitManualAdjustment(OperatorContext context,
        SubmitManualAdjustmentRequestBody request);

    /**
     * 批准人工调整并过账
     *
     * @param context 复核人会话
     * @param manualAdjustmentId 调整主键
     * @param request 确认摘要
     * @return 过账结果
     */
    ReviewManualAdjustmentResponseBody approveManualAdjustment(OperatorContext context, String manualAdjustmentId,
        ReviewManualAdjustmentRequestBody request);

    /**
     * 驳回人工调整
     *
     * @param context 复核人会话
     * @param manualAdjustmentId 调整主键
     * @param request 确认摘要
     * @return REJECTED 结果
     */
    ReviewManualAdjustmentResponseBody rejectManualAdjustment(OperatorContext context, String manualAdjustmentId,
        ReviewManualAdjustmentRequestBody request);
}
