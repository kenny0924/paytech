package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.AppendBankStatementLinesRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankMatchListResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankMatchResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankStatementCompletionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankStatementLineDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankStatementLineResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.BankStatementResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.CreateBankMatchesRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.ExpectedBankMovementResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationBankModels.StartBankStatementImportRequestBody;

/**
 * reconciliation 银行对账的本地端口：对账单分段导入、银行流水、预期银行流水与银行匹配
 * <li>余额恒等式、去重、匹配建议与带符号合计校验归 reconciliation；BFF 只逐个转发。</li>
 *
 * @author Claude
 */
public interface ReconciliationBankClient {

    /**
     * 银行对账单头（详情页）
     *
     * @param bankStatementId 对账单主键
     * @return 对账单头与导入进度；不存在时 reconciliation 返回 404 BANK_STATEMENT_NOT_FOUND
     */
    BankStatementResponseBody getBankStatement(String bankStatementId);

    /**
     * 银行流水详情（详情页）
     *
     * @param bankStatementLineId 银行流水主键
     * @return 流水、所属对账单、全部银行匹配（含预期银行流水）与未结异常；不存在时 reconciliation 返回 404
     *         BANK_STATEMENT_LINE_NOT_FOUND
     */
    BankStatementLineDetailResponseBody getBankStatementLine(String bankStatementLineId);

    /**
     * 银行对账单分页
     *
     * @param cashAccountId 可选银行账户主键
     * @param status 可选状态
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 对账单分页
     */
    PagingResponseBody<BankStatementResponseBody> listBankStatements(String cashAccountId, String status, int page,
        int size);

    /**
     * 一张对账单的流水分页
     *
     * @param bankStatementId 对账单主键
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 流水分页
     */
    PagingResponseBody<BankStatementLineResponseBody> listBankStatementLinesOfStatement(String bankStatementId,
        int page, int size);

    /**
     * 银行账户下的流水分页
     *
     * @param cashAccountId 银行账户主键
     * @param matchStatus 可选匹配状态
     * @param direction 可选方向
     * @param valueDateFrom 可选起息日起
     * @param valueDateTo 可选起息日止
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 流水分页
     */
    PagingResponseBody<BankStatementLineResponseBody> listBankStatementLines(String cashAccountId, String matchStatus,
        String direction, String valueDateFrom, String valueDateTo, int page, int size);

    /**
     * 开始导入银行对账单
     *
     * @param context 上传人会话
     * @param request 银行账户、头部与文件信息
     * @return RECEIVING 对账单
     */
    BankStatementResponseBody startBankStatementImport(OperatorContext context,
        StartBankStatementImportRequestBody request);

    /**
     * 追加一段银行流水
     *
     * @param context 上传人会话
     * @param bankStatementId 对账单主键
     * @param request 1–100 行
     * @return 对账单与接收进度
     */
    BankStatementResponseBody appendBankStatementLines(OperatorContext context, String bankStatementId,
        AppendBankStatementLinesRequestBody request);

    /**
     * 完成导入银行对账单
     *
     * @param context 上传人会话
     * @param bankStatementId 对账单主键
     * @return IMPORTED 或 REJECTED 与逐行原因
     */
    BankStatementCompletionResponseBody completeBankStatementImport(OperatorContext context, String bankStatementId);

    /**
     * 预期银行流水分页
     *
     * @param cashAccountId 可选银行账户主键
     * @param movementType 可选来源
     * @param sourceId 可选来源 ID
     * @param status 可选状态
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 预期银行流水分页
     */
    PagingResponseBody<ExpectedBankMovementResponseBody> listExpectedBankMovements(String cashAccountId,
        String movementType, String sourceId, String status, int page, int size);

    /**
     * 银行匹配分页
     *
     * @param bankStatementLineId 可选银行流水主键
     * @param expectedMovementId 可选预期银行流水主键
     * @param status 可选状态
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 银行匹配分页
     */
    PagingResponseBody<BankMatchResponseBody> listBankMatches(String bankStatementLineId, String expectedMovementId,
        String status, int page, int size);

    /**
     * 确认匹配建议
     *
     * @param context 操作人会话
     * @param bankMatchId 匹配主键
     * @return CONFIRMED 匹配
     */
    BankMatchResponseBody confirmBankMatch(OperatorContext context, String bankMatchId);

    /**
     * 拒绝匹配建议
     *
     * @param context 操作人会话
     * @param bankMatchId 匹配主键
     * @return REJECTED 匹配
     */
    BankMatchResponseBody rejectBankMatch(OperatorContext context, String bankMatchId);

    /**
     * 人工多选匹配
     *
     * @param context 操作人会话
     * @param request 一条银行流水与多条分配
     * @return 已确认的匹配
     */
    BankMatchListResponseBody createBankMatches(OperatorContext context, CreateBankMatchesRequestBody request);
}
