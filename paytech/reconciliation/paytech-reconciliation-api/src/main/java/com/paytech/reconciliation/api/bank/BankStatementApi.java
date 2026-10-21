package com.paytech.reconciliation.api.bank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.ReconciliationHeaders;
import com.paytech.reconciliation.api.bank.request.AppendBankStatementLinesRequestBody;
import com.paytech.reconciliation.api.bank.request.StartBankStatementImportRequestBody;
import com.paytech.reconciliation.api.bank.response.BankStatementCompletionResponseBody;
import com.paytech.reconciliation.api.bank.response.BankStatementLineResponseBody;
import com.paytech.reconciliation.api.bank.response.BankStatementResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 银行对账单分段导入与银行流水查询（UC-R11、R17；technique D-01）
 * <li>与渠道账单相同的「开始 → 分段追加行（每段 ≤ 100 行）→ 完成」三步；单张对账单上限 20,000 行，服务不保存原文件。</li>
 * <li>完成导入时校验期初余额 + Σ 带符号流水 = 期末余额，不通过整单 REJECTED；与同一银行账户上一张对账单接不上时导入成功并开不阻塞的
 * BANK_STATEMENT_GAP；同一银行账户 + 银行交易号已入库的流水跳过并计数。导入后登记本地匹配建议任务。</li>
 * <li>运营写命令的操作人取自 {@link ReconciliationHeaders} 身份头；查询不带。</li>
 */
@HttpExchange(url = "/reconciliation/v1", accept = "application/json")
public interface BankStatementApi {

    /**
     * 开始导入银行对账单（RECEIVING）
     *
     * @param actorAccountId 上传人 Account ID；缺失或格式错误时 400
     * @param sessionId 上传人会话 ID；缺失时 400
     * @param body 银行账户、对账单头、文件名、文件 SHA-256 与声明行数
     * @return 201 与对账单；银行账户不存在或不是 ACTIVE、末四位不一致、币种不等于账户币种、期间倒置、同一银行账户对账单编号已存在时 400
     */
    @PostExchange("/bank-statements")
    ResponseEntity<BankStatementResponseBody> startBankStatementImport(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @Valid @RequestBody StartBankStatementImportRequestBody body);

    /**
     * 追加一段银行流水行
     * <li>按 lineNo 幂等：同号同内容忽略，同号不同内容拒绝。</li>
     *
     * @param actorAccountId 上传人 Account ID，必须等于开始导入的上传人；缺失或格式错误时 400
     * @param sessionId 上传人会话 ID；缺失时 400
     * @param bankStatementId 银行对账单主键
     * @param body 1–100 行
     * @return 200 与对账单当前接收进度；对账单不是 RECEIVING、行号超出声明行数或同号不同内容时 400；并发追加时 409
     */
    @PostExchange("/bank-statements/{bankStatementId}/lines")
    BankStatementResponseBody appendBankStatementLines(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("bankStatementId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bankStatementId,
        @Valid @RequestBody AppendBankStatementLinesRequestBody body);

    /**
     * 完成导入：校验余额恒等式与连续性并入库流水
     *
     * @param actorAccountId 上传人 Account ID，必须等于开始导入的上传人；缺失或格式错误时 400
     * @param sessionId 上传人会话 ID；缺失时 400
     * @param bankStatementId 银行对账单主键
     * @return 200 与结果：通过为 IMPORTED（含重复跳过行数与连续性异常主键）；不通过为 REJECTED 并带汇总原因与逐行原因；已完成时返回当前
     *         状态；并发时 409
     */
    @PostExchange("/bank-statements/{bankStatementId}/completion")
    BankStatementCompletionResponseBody completeBankStatementImport(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("bankStatementId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bankStatementId);

    /**
     * 分页查询银行对账单
     *
     * @param cashAccountId 可选银行账户主键
     * @param status 可选状态：RECEIVING、IMPORTED、REJECTED
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按期间结束日降序、主键降序的分页结果；参数非法时 400
     */
    @GetExchange("/bank-statements")
    PagingResponseBody<BankStatementResponseBody> listBankStatements(
        @RequestParam(value = "cashAccountId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String cashAccountId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "RECEIVING|IMPORTED|REJECTED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 分页查询一张对账单的银行流水
     *
     * @param bankStatementId 银行对账单主键
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按行号升序的分页结果；对账单不存在时 400
     */
    @GetExchange("/bank-statements/{bankStatementId}/lines")
    PagingResponseBody<BankStatementLineResponseBody> listBankStatementLinesOfStatement(
        @PathVariable("bankStatementId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bankStatementId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 跨对账单分页查询银行流水（如未匹配流水）
     *
     * @param cashAccountId 银行账户主键，必填
     * @param matchStatus 可选匹配状态：UNMATCHED、PARTIALLY_MATCHED、MATCHED、EXCEPTION、SUSPENDED
     * @param direction 可选方向：CREDIT、DEBIT
     * @param valueDateFrom 可选起息日起（含），YYYY-MM-DD
     * @param valueDateTo 可选起息日止（含），YYYY-MM-DD
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按起息日升序、主键升序的分页结果；参数非法时 400
     */
    @GetExchange("/bank-statement-lines")
    PagingResponseBody<BankStatementLineResponseBody> listBankStatementLines(
        @RequestParam("cashAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String cashAccountId,
        @RequestParam(value = "matchStatus", required = false)
        @Pattern(regexp = "UNMATCHED|PARTIALLY_MATCHED|MATCHED|EXCEPTION|SUSPENDED") String matchStatus,
        @RequestParam(value = "direction", required = false) @Pattern(regexp = "CREDIT|DEBIT") String direction,
        @RequestParam(value = "valueDateFrom", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String valueDateFrom,
        @RequestParam(value = "valueDateTo", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String valueDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
