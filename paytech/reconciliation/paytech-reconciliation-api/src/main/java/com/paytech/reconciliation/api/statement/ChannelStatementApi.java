package com.paytech.reconciliation.api.statement;

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
import com.paytech.reconciliation.api.statement.request.AppendChannelStatementLinesRequestBody;
import com.paytech.reconciliation.api.statement.request.StartChannelStatementImportRequestBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementCompletionResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementDetailResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementLineResponseBody;
import com.paytech.reconciliation.api.statement.response.ChannelStatementResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 渠道账单分段导入、作废与查询（UC-R06、R08、R17；technique D-01）
 * <li>浏览器本地解析标准 CSV 并计算整个文件的 SHA-256，按「开始 → 分段追加行（每段 ≤ 100 行）→ 完成」三步提交 JSON；单张账单上限 20,000 行。
 * 服务不保存原文件。</li>
 * <li>开始导入建为 RECEIVING；完成导入时一次性校验行数、行号连续、合计与账单头一致、币种一致，通过进入 MATCHING 并登记本地匹配任务，
 * 不通过整单 REJECTED 并返回逐行原因。</li>
 * <li>运营写命令的操作人取自 {@link ReconciliationHeaders} 身份头，仅经 Operations BFF 转发；查询不带。</li>
 */
@HttpExchange(url = "/reconciliation/v1/channel-statements", accept = "application/json")
public interface ChannelStatementApi {

    /**
     * 开始导入渠道账单（RECEIVING）
     * <li>revision 取同一清算账户 + 批次号已有最大版本 + 1。</li>
     *
     * @param actorAccountId 上传人 Account ID；缺失或格式错误时 400
     * @param sessionId 上传人会话 ID；缺失时 400
     * @param body 账单头、文件名、文件 SHA-256 与声明行数
     * @return 201 与账单；清算账户编码不存在或停用、币种不等于清算账户币种、同批次号已有 RECONCILED / CASH_CONFIRMED 版本时 400；
     *         同批次号已有 RECEIVING / MATCHING 版本时 400（须先作废）
     */
    @PostExchange
    ResponseEntity<ChannelStatementResponseBody> startChannelStatementImport(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @Valid @RequestBody StartChannelStatementImportRequestBody body);

    /**
     * 追加一段账单明细行
     * <li>按 lineNo 幂等：同号同内容忽略，同号不同内容拒绝。</li>
     *
     * @param actorAccountId 上传人 Account ID，必须等于开始导入的上传人；缺失或格式错误时 400
     * @param sessionId 上传人会话 ID；缺失时 400
     * @param statementId 渠道账单主键
     * @param body 1–100 行
     * @return 200 与账单当前接收进度；账单不是 RECEIVING、行号超出声明行数、同号不同内容或超过 20,000 行时 400；并发追加时 409
     */
    @PostExchange("/{statementId}/lines")
    ChannelStatementResponseBody appendChannelStatementLines(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("statementId") @Pattern(regexp = "[1-9][0-9]{0,18}") String statementId,
        @Valid @RequestBody AppendChannelStatementLinesRequestBody body);

    /**
     * 完成导入：一次性校验并进入匹配
     *
     * @param actorAccountId 上传人 Account ID，必须等于开始导入的上传人；缺失或格式错误时 400
     * @param sessionId 上传人会话 ID；缺失时 400
     * @param statementId 渠道账单主键
     * @return 200 与结果：通过为 MATCHING；校验不通过为 REJECTED 并带汇总原因与逐行原因（业务结果，不是 HTTP 错误）；账单已是 MATCHING /
     *         REJECTED 时返回当前状态（逐行原因只在首次完成时返回）；账单已作废时 400；并发时 409
     */
    @PostExchange("/{statementId}/completion")
    ChannelStatementCompletionResponseBody completeChannelStatementImport(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("statementId") @Pattern(regexp = "[1-9][0-9]{0,18}") String statementId);

    /**
     * 作废未对平的渠道账单（UC-R08）
     * <li>释放行匹配与实际费用、回退平台流水进度，本账单开放异常 AUTO_CLOSED。</li>
     *
     * @param actorAccountId 操作人 Account ID；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID；缺失时 400
     * @param statementId 渠道账单主键
     * @return 200 与 VOIDED 账单；已作废时返回当前结果；状态不是 RECEIVING / MATCHING（含已对平）或有待复核处置时 400；并发时 409
     */
    @PostExchange("/{statementId}/void")
    ChannelStatementResponseBody voidChannelStatement(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("statementId") @Pattern(regexp = "[1-9][0-9]{0,18}") String statementId);

    /**
     * 分页查询渠道账单
     *
     * @param clearingAccountId 可选清算账户主键
     * @param channelBatchRef 可选渠道批次号（精确）
     * @param status 可选状态：RECEIVING、REJECTED、MATCHING、VOIDED、RECONCILED、CASH_CONFIRMED
     * @param settlementDateFrom 可选结算日起（含），YYYY-MM-DD
     * @param settlementDateTo 可选结算日止（含），YYYY-MM-DD
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按结算日降序、主键降序的分页结果；参数非法时 400
     */
    @GetExchange
    PagingResponseBody<ChannelStatementResponseBody> listChannelStatements(
        @RequestParam(value = "clearingAccountId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String clearingAccountId,
        @RequestParam(value = "channelBatchRef", required = false) @Pattern(regexp = "[\\x21-\\x7E]{1,128}") String channelBatchRef,
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "RECEIVING|REJECTED|MATCHING|VOIDED|RECONCILED|CASH_CONFIRMED") String status,
        @RequestParam(value = "settlementDateFrom", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String settlementDateFrom,
        @RequestParam(value = "settlementDateTo", required = false) @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String settlementDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询渠道账单详情
     *
     * @param statementId 渠道账单主键
     * @return 账单、按状态的行计数与对平门禁四项的满足情况；不存在时 400
     */
    @GetExchange("/{statementId}")
    ChannelStatementDetailResponseBody getChannelStatement(
        @PathVariable("statementId") @Pattern(regexp = "[1-9][0-9]{0,18}") String statementId);

    /**
     * 分页查询渠道账单行
     *
     * @param statementId 渠道账单主键
     * @param matchStatus 可选匹配状态：UNMATCHED、MATCHED、EXCEPTION
     * @param lineType 可选行类型：PAYMENT、REFUND、CHARGEBACK、FEE、ADJUSTMENT
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按行号升序的分页结果，含匹配的平台流水、实际费用与归类处置；账单不存在时 400
     */
    @GetExchange("/{statementId}/lines")
    PagingResponseBody<ChannelStatementLineResponseBody> listChannelStatementLines(
        @PathVariable("statementId") @Pattern(regexp = "[1-9][0-9]{0,18}") String statementId,
        @RequestParam(value = "matchStatus", required = false) @Pattern(regexp = "UNMATCHED|MATCHED|EXCEPTION") String matchStatus,
        @RequestParam(value = "lineType", required = false)
        @Pattern(regexp = "PAYMENT|REFUND|CHARGEBACK|FEE|ADJUSTMENT") String lineType,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
