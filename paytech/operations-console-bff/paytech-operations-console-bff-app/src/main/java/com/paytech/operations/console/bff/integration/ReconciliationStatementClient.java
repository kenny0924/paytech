package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.AppendChannelStatementLinesRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementCompletionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementLineResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.ChannelStatementResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.PlatformTxnResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationStatementModels.StartChannelStatementImportRequestBody;

/**
 * reconciliation 渠道账单与平台流水的本地端口；分段导入校验、匹配、门禁与作废归 reconciliation
 * <li>浏览器本地解析 CSV 后按“开始 → 追加 → 完成”分段发送（D-01），BFF 逐段转发、不聚合也不缓存行。</li>
 *
 * @author Claude
 */
public interface ReconciliationStatementClient {

    /**
     * 渠道账单分页
     *
     * @param clearingAccountId 可选清算账户主键
     * @param channelBatchRef 可选批次号
     * @param status 可选状态
     * @param settlementDateFrom 可选结算日起（YYYY-MM-DD）
     * @param settlementDateTo 可选结算日止（YYYY-MM-DD）
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 账单分页
     */
    PagingResponseBody<ChannelStatementResponseBody> listChannelStatements(String clearingAccountId,
        String channelBatchRef, String status, String settlementDateFrom, String settlementDateTo, int page, int size);

    /**
     * 渠道账单详情与对平门禁
     *
     * @param statementId 账单主键
     * @return 账单、行计数与门禁
     */
    ChannelStatementDetailResponseBody getChannelStatement(String statementId);

    /**
     * 渠道账单行分页
     *
     * @param statementId 账单主键
     * @param matchStatus 可选匹配状态
     * @param lineType 可选行类型
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 账单行分页
     */
    PagingResponseBody<ChannelStatementLineResponseBody> listChannelStatementLines(String statementId,
        String matchStatus, String lineType, int page, int size);

    /**
     * 开始导入渠道账单
     *
     * @param context 上传人会话
     * @param request 账单头与文件信息
     * @return RECEIVING 账单
     */
    ChannelStatementResponseBody startChannelStatementImport(OperatorContext context,
        StartChannelStatementImportRequestBody request);

    /**
     * 追加一段渠道账单行
     *
     * @param context 上传人会话
     * @param statementId 账单主键
     * @param request 1–100 行
     * @return 账单与接收进度
     */
    ChannelStatementResponseBody appendChannelStatementLines(OperatorContext context, String statementId,
        AppendChannelStatementLinesRequestBody request);

    /**
     * 完成导入渠道账单
     *
     * @param context 上传人会话
     * @param statementId 账单主键
     * @return MATCHING 或 REJECTED 与逐行原因
     */
    ChannelStatementCompletionResponseBody completeChannelStatementImport(OperatorContext context, String statementId);

    /**
     * 作废渠道账单
     *
     * @param context 操作人会话
     * @param statementId 账单主键
     * @return VOIDED 账单
     */
    ChannelStatementResponseBody voidChannelStatement(OperatorContext context, String statementId);

    /**
     * 平台流水分页
     *
     * @param clearingAccountId 可选清算账户主键
     * @param statementId 可选渠道账单主键（本账单匹配到的平台流水）
     * @param status 可选状态
     * @param direction 可选方向
     * @param externalTransactionId 可选外部交易号
     * @param businessDateFrom 可选业务日起
     * @param businessDateTo 可选业务日止
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 平台流水分页
     */
    PagingResponseBody<PlatformTxnResponseBody> listPlatformTxns(String clearingAccountId, String statementId, String status,
        String direction, String externalTransactionId, String businessDateFrom, String businessDateTo, int page,
        int size);
}
