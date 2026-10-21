package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.OpenReconExceptionRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconExceptionDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconExceptionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconResolutionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReviewReconResolutionRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.SubmitReconResolutionRequestBody;

/**
 * reconciliation 对账异常、处置与在途汇总的本地端口
 * <li>可用处置、归类码范围、提交人不能复核与处置应用全部归 reconciliation；BFF 不判断权限点与 Maker / Checker。</li>
 *
 * @author Claude
 */
public interface ReconciliationExceptionClient {

    /**
     * 对账异常分页，按账龄从长到短
     *
     * @param status 可选状态；不传为未结
     * @param exceptionType 可选异常类型
     * @param reconLayer 可选对账层
     * @param clearingAccountId 可选清算账户主键
     * @param cashAccountId 可选银行账户主键
     * @param statementId 可选渠道账单主键
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 异常分页
     */
    PagingResponseBody<ReconExceptionResponseBody> listReconExceptions(String status, String exceptionType,
        String reconLayer, String clearingAccountId, String cashAccountId, String statementId, int page, int size);

    /**
     * 对账异常详情（不含 BFF 账户标识）
     *
     * @param exceptionId 异常主键
     * @return 异常、来源摘要、可用处置与处置历史
     */
    ReconExceptionDetailResponseBody getReconException(String exceptionId);

    /**
     * 人工开立对账异常
     *
     * @param context 操作人会话
     * @param request 来源对象
     * @return 新异常
     */
    ReconExceptionResponseBody openReconException(OperatorContext context, OpenReconExceptionRequestBody request);

    /**
     * 提交异常处置
     *
     * @param context 提交人会话
     * @param exceptionId 异常主键
     * @param request 处置内容
     * @return PENDING_CHECK 处置
     */
    ReconResolutionResponseBody submitReconResolution(OperatorContext context, String exceptionId,
        SubmitReconResolutionRequestBody request);

    /**
     * 复核异常处置
     *
     * @param context 复核人会话
     * @param resolutionId 处置主键
     * @param request 决定与确认摘要
     * @return APPLIED 或 REJECTED 处置
     */
    ReconResolutionResponseBody reviewReconResolution(OperatorContext context, String resolutionId,
        ReviewReconResolutionRequestBody request);

    /**
     * 在途汇总
     *
     * @param entityNo 可选经营主体编号
     * @param currency 币种
     * @return 在途账单、开放异常账龄段与未匹配银行流水
     */
    ReconSummaryResponseBody getReconSummary(String entityNo, String currency);
}
