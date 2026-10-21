package com.paytech.reconciliation.api.cash;

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
import com.paytech.reconciliation.api.cash.request.CreateCashAccountRequestBody;
import com.paytech.reconciliation.api.cash.request.ReviewCashAccountRequestBody;
import com.paytech.reconciliation.api.cash.response.CashAccountResponseBody;
import com.paytech.reconciliation.api.cash.response.CurrentCashAccountResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 平台银行账户维护、复核与查询（UC-R02、R04、R17）
 * <li>新建与关闭都经复核：新建为 PENDING_VERIFICATION，复核通过 ACTIVE；申请关闭记 pendingAction = CLOSE，复核通过 CLOSED，拒绝清空申请。
 * 复核人必须不同于最近一次提交人，并原样确认提交时的 contentHash。不改用途：换用途即关旧户、开新户。</li>
 * <li>银行账号在事务外经 Vault Protect（CASH_ACCOUNT_NUMBER_V1，REFERENCE）后只存令牌与末四位；任何响应都不返回明文。新建请求体含明文账号，
 * 服务必须把本接口前缀配置为 MVC 日志 metadata-only。</li>
 * <li>运营写命令的操作人取自 {@link ReconciliationHeaders} 身份头；当前账户查询是服务间内部接口（payout、tax），不带操作人头。</li>
 */
@HttpExchange(url = "/reconciliation/v1/cash-accounts", accept = "application/json")
public interface CashAccountApi {

    /**
     * 提交新建银行账户（PENDING_VERIFICATION）
     *
     * @param actorAccountId 提交人 Account ID；缺失或格式错误时 400
     * @param sessionId 提交人会话 ID；缺失时 400
     * @param body 主体、用途、银行、币种与银行账号
     * @return 201 与银行账户（含待复核 contentHash，不含明文账号）；Vault 不可用时 503
     */
    @PostExchange
    ResponseEntity<CashAccountResponseBody> createCashAccount(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @Valid @RequestBody CreateCashAccountRequestBody body);

    /**
     * 申请关闭银行账户
     *
     * @param actorAccountId 提交人 Account ID；缺失或格式错误时 400
     * @param sessionId 提交人会话 ID；缺失时 400
     * @param cashAccountId 银行账户主键
     * @return 200 与 pendingAction = CLOSE 的银行账户及新的 contentHash；账户不是 ACTIVE 或已有关闭申请时 400；并发修改时 409
     */
    @PostExchange("/{cashAccountId}/closure")
    CashAccountResponseBody requestCashAccountClosure(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("cashAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String cashAccountId);

    /**
     * 复核银行账户的新建或关闭申请
     *
     * @param actorAccountId 复核人 Account ID，必须不同于提交人；缺失或格式错误时 400
     * @param sessionId 复核人会话 ID；缺失时 400
     * @param cashAccountId 银行账户主键
     * @param body 复核决定与确认的内容摘要
     * @return 200 与复核后的银行账户；没有待复核内容、同人复核、摘要不符时 400；批准新建时同主体 + 币种 + 用途已有 ACTIVE 账户 400；
     *         并发复核时 409
     */
    @PostExchange("/{cashAccountId}/approval")
    CashAccountResponseBody reviewCashAccount(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("cashAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String cashAccountId,
        @Valid @RequestBody ReviewCashAccountRequestBody body);

    /**
     * 分页查询银行账户
     *
     * @param entityType 可选主体类型：OPERATING_ENTITY、FINANCE_ENTITY
     * @param entityId 可选主体标识，与 entityType 一起使用
     * @param currency 可选币种
     * @param accountPurpose 可选用途：CHANNEL_SETTLEMENT、PAYOUT、TAX
     * @param status 可选状态：PENDING_VERIFICATION、ACTIVE、SUSPENDED、CLOSED
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按更新时间降序、主键降序的分页结果，只含末四位；参数非法时 400
     */
    @GetExchange
    PagingResponseBody<CashAccountResponseBody> listCashAccounts(
        @RequestParam(value = "entityType", required = false) @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam(value = "entityId", required = false) @Pattern(regexp = "[A-Za-z0-9]{1,64}") String entityId,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "accountPurpose", required = false) @Pattern(regexp = "CHANNEL_SETTLEMENT|PAYOUT|TAX") String accountPurpose,
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "PENDING_VERIFICATION|ACTIVE|SUSPENDED|CLOSED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询银行账户详情
     *
     * @param cashAccountId 银行账户主键
     * @return 银行账户，只含末四位；不存在时 400
     */
    @GetExchange("/{cashAccountId}")
    CashAccountResponseBody getCashAccount(
        @PathVariable("cashAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String cashAccountId);

    /**
     * 查询主体 + 币种 + 用途的当前 ACTIVE 银行账户（UC-R04，服务间内部接口）
     *
     * @param entityType 主体类型：OPERATING_ENTITY、FINANCE_ENTITY
     * @param entityId 主体标识：经营主体为 entityNo，财务主体为 ledger 主键十进制字符串
     * @param currency 币种
     * @param purpose 用途：CHANNEL_SETTLEMENT、PAYOUT、TAX
     * @return 200 与银行账户主键和末四位；没有 ACTIVE 账户时 400
     */
    @GetExchange("/current")
    CurrentCashAccountResponseBody getCurrentCashAccount(
        @RequestParam("entityType") @NotBlank @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam("entityId") @NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,64}") String entityId,
        @RequestParam("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam("purpose") @NotBlank @Pattern(regexp = "CHANNEL_SETTLEMENT|PAYOUT|TAX") String purpose);
}
