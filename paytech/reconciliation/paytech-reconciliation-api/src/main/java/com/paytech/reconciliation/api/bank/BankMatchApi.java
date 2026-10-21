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
import com.paytech.reconciliation.api.bank.request.CreateBankMatchesRequestBody;
import com.paytech.reconciliation.api.bank.response.BankMatchListResponseBody;
import com.paytech.reconciliation.api.bank.response.BankMatchResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 银行匹配：系统建议的确认 / 拒绝与人工多选匹配（UC-R13）
 * <li>系统只给一对一建议（PROPOSED）：同一银行账户、方向与币种一致、金额相等、起息日在预期日窗口内；确认均为人工。</li>
 * <li>确认时先锁银行流水再锁预期银行流水（ID 升序）；同一流水已确认的带符号合计不超过流水金额，每条分配不超过预期剩余金额。</li>
 * <li>每条确认的渠道（CHANNEL_SETTLEMENT）匹配在同一本地事务登记一次 ChannelSettlementConfirmed 发布，按匹配 ID 幂等。</li>
 * <li>运营写命令的操作人取自 {@link ReconciliationHeaders} 身份头；查询不带。</li>
 */
@HttpExchange(url = "/reconciliation/v1/bank-matches", accept = "application/json")
public interface BankMatchApi {

    /**
     * 分页查询银行匹配
     *
     * @param bankStatementLineId 可选银行流水主键；与 expectedMovementId 至少给一个
     * @param expectedMovementId 可选预期银行流水主键
     * @param status 可选状态：PROPOSED、CONFIRMED、REJECTED
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按创建时间升序、主键升序的分页结果；两个主键都没给或参数非法时 400
     */
    @GetExchange
    PagingResponseBody<BankMatchResponseBody> listBankMatches(
        @RequestParam(value = "bankStatementLineId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}")
        String bankStatementLineId,
        @RequestParam(value = "expectedMovementId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}")
        String expectedMovementId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "PROPOSED|CONFIRMED|REJECTED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 确认系统建议
     *
     * @param actorAccountId 操作人 Account ID；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID；缺失时 400
     * @param bankMatchId 银行匹配主键
     * @return 200 与 CONFIRMED 匹配；已确认时返回当前结果；建议已拒绝、流水或预期流水剩余金额不足时 400；并发时 409
     */
    @PostExchange("/{bankMatchId}/confirmation")
    BankMatchResponseBody confirmBankMatch(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("bankMatchId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bankMatchId);

    /**
     * 拒绝系统建议
     *
     * @param actorAccountId 操作人 Account ID；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID；缺失时 400
     * @param bankMatchId 银行匹配主键
     * @return 200 与 REJECTED 匹配；已拒绝时返回当前结果；建议已确认时 400；并发时 409
     */
    @PostExchange("/{bankMatchId}/rejection")
    BankMatchResponseBody rejectBankMatch(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("bankMatchId") @Pattern(regexp = "[1-9][0-9]{0,18}") String bankMatchId);

    /**
     * 人工多选匹配：一条银行流水分配给多条预期银行流水，直接 CONFIRMED
     *
     * @param actorAccountId 操作人 Account ID；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID；缺失时 400
     * @param body 银行流水与分配
     * @return 201 与新建的已确认匹配；预期流水与流水不是同一银行账户、带符号合计超过流水剩余金额、单条超过预期剩余金额、
     *         同一预期流水重复出现时 400；并发时 409
     */
    @PostExchange
    ResponseEntity<BankMatchListResponseBody> createBankMatches(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @Valid @RequestBody CreateBankMatchesRequestBody body);
}
