package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.ReviewPeriodCloseRequestBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.StartPeriodReopenRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * ledger 期间关账与重开的本地端口；关账步骤、摘要、复核规则与冲回归 ledger
 * <li>命令以会话 Account ID 与 Session ID 作为操作人 Header 转发；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface FinancePeriodCloseClient {

    /**
     * 期间的关账/重开流程分页，按序号倒序
     *
     * @param accountingPeriodId 会计期间主键
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 流程摘要分页
     */
    PagingResponseBody<PeriodCloseResponseBody> listPeriodCloses(String accountingPeriodId, int page, int size);

    /**
     * 流程详情
     *
     * @param accountingPeriodId 会计期间主键
     * @param closeNo 期间内流程序号
     * @return 摘要、步骤、账簿证据与审批记录
     */
    PeriodCloseDetailResponseBody getPeriodClose(String accountingPeriodId, String closeNo);

    /**
     * 发起期间关账（ledger 同步执行 7 个步骤）
     *
     * @param context 编制人会话
     * @param accountingPeriodId 会计期间主键
     * @return PENDING_CHECK 或 FAILED 流程摘要
     */
    PeriodCloseResponseBody startPeriodClose(OperatorContext context, String accountingPeriodId);

    /**
     * 发起期间重开
     *
     * @param context 编制人会话
     * @param accountingPeriodId 会计期间主键
     * @param request 重开原因与申请编号
     * @return PENDING_CHECK 的 REOPEN 流程摘要
     */
    PeriodCloseResponseBody startPeriodReopen(OperatorContext context, String accountingPeriodId,
        StartPeriodReopenRequestBody request);

    /**
     * 批准关账/重开
     *
     * @param context 复核人会话
     * @param accountingPeriodId 会计期间主键
     * @param closeNo 期间内流程序号
     * @param request 确认摘要
     * @return COMPLETED 流程摘要
     */
    PeriodCloseResponseBody approvePeriodClose(OperatorContext context, String accountingPeriodId, String closeNo,
        ReviewPeriodCloseRequestBody request);

    /**
     * 驳回关账/重开
     *
     * @param context 复核人会话
     * @param accountingPeriodId 会计期间主键
     * @param closeNo 期间内流程序号
     * @param request 确认摘要
     * @return REJECTED 流程摘要
     */
    PeriodCloseResponseBody rejectPeriodClose(OperatorContext context, String accountingPeriodId, String closeNo,
        ReviewPeriodCloseRequestBody request);
}
