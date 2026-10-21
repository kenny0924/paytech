package com.paytech.reconciliation.api.exception;

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
import com.paytech.reconciliation.api.exception.request.OpenReconExceptionRequestBody;
import com.paytech.reconciliation.api.exception.request.SubmitReconResolutionRequestBody;
import com.paytech.reconciliation.api.exception.response.ReconExceptionDetailResponseBody;
import com.paytech.reconciliation.api.exception.response.ReconExceptionResponseBody;
import com.paytech.reconciliation.api.exception.response.ReconResolutionResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 对账异常：人工开立、查询与提交处置（technique D-02，UC-R15、R17）
 * <li>异常挂在本库对象上，主体、清算账户 / 银行账户、币种由来源父链确定；差异金额由系统算出，不让人填。同一对象同一类型只有一个未结异常。</li>
 * <li>提交处置（Maker）后异常 UNDER_REVIEW，处置内容冻结为 contentHash，由 {@link ReconResolutionApi} 复核；一个异常只有一条最终 APPLIED 处置。</li>
 * <li>运营写命令的操作人取自 {@link ReconciliationHeaders} 身份头；查询不带。</li>
 */
@HttpExchange(url = "/reconciliation/v1/recon-exceptions", accept = "application/json")
public interface ReconExceptionApi {

    /**
     * 人工开立不阻塞的异常（D-02）
     * <li>类型由来源对象决定：PLATFORM_TXN → PLATFORM_ITEM_UNSETTLED，EXPECTED_BANK_MOVEMENT → BANK_AMOUNT_MISMATCH，
     * BANK_STATEMENT_LINE → BANK_LINE_UNMATCHED；差异金额取对象当前未完成金额。</li>
     *
     * @param actorAccountId 开立人 Account ID；缺失或格式错误时 400
     * @param sessionId 开立人会话 ID；缺失时 400
     * @param body 来源对象
     * @return 201 与异常；来源对象不存在、已完成（未完成金额为 0）或已有同类型未结异常时 400；并发时 409
     */
    @PostExchange
    ResponseEntity<ReconExceptionResponseBody> openReconException(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @Valid @RequestBody OpenReconExceptionRequestBody body);

    /**
     * 分页查询对账异常，按账龄从长到短（开立时间升序、主键升序）
     *
     * @param status 可选状态：OPEN、UNDER_REVIEW、RESOLVED、AUTO_CLOSED；省略时只查未结（OPEN、UNDER_REVIEW）
     * @param exceptionType 可选异常类型
     * @param reconLayer 可选对账层：TRANSACTION、BANK
     * @param clearingAccountId 可选清算账户主键
     * @param cashAccountId 可选银行账户主键
     * @param statementId 可选渠道账单主键
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 含账龄天数的分页结果；参数非法时 400
     */
    @GetExchange
    PagingResponseBody<ReconExceptionResponseBody> listReconExceptions(
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "OPEN|UNDER_REVIEW|RESOLVED|AUTO_CLOSED") String status,
        @RequestParam(value = "exceptionType", required = false) @Pattern(regexp = "[A-Z][A-Z_]{0,31}") String exceptionType,
        @RequestParam(value = "reconLayer", required = false) @Pattern(regexp = "TRANSACTION|BANK") String reconLayer,
        @RequestParam(value = "clearingAccountId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String clearingAccountId,
        @RequestParam(value = "cashAccountId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String cashAccountId,
        @RequestParam(value = "statementId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String statementId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询对账异常详情
     *
     * @param exceptionId 异常主键
     * @return 异常、来源对象摘要、当前可用的处置方式与归类码、全部处置（序号升序）；不存在时 400
     */
    @GetExchange("/{exceptionId}")
    ReconExceptionDetailResponseBody getReconException(
        @PathVariable("exceptionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String exceptionId);

    /**
     * 提交处置（Maker）
     *
     * @param actorAccountId 提交人 Account ID；缺失或格式错误时 400
     * @param sessionId 提交人会话 ID；缺失时 400
     * @param exceptionId 异常主键
     * @param body 处置方式与对应输入
     * @return 201 与 PENDING_CHECK 处置（含 contentHash），异常转 UNDER_REVIEW；异常不是 OPEN、处置方式或归类码不被来源对象允许、
     *         归类金额不等于差异金额、重新匹配目标不合格时 400；并发时 409
     */
    @PostExchange("/{exceptionId}/resolutions")
    ResponseEntity<ReconResolutionResponseBody> submitReconResolution(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("exceptionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String exceptionId,
        @Valid @RequestBody SubmitReconResolutionRequestBody body);
}
