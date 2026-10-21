package com.paytech.reconciliation.api.clearing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.ReconciliationHeaders;
import com.paytech.reconciliation.api.clearing.request.AddClearingAccountMidRequestBody;
import com.paytech.reconciliation.api.clearing.request.ChangeClearingAccountStatusRequestBody;
import com.paytech.reconciliation.api.clearing.request.CreateClearingAccountRequestBody;
import com.paytech.reconciliation.api.clearing.request.UpdateClearingAccountSettingsRequestBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountResponseBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountSummaryResponseBody;
import com.paytech.reconciliation.api.clearing.response.UnassignedMidListResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 渠道清算账户维护与查询（UC-R01、R17）
 * <li>清算账户是一个经营主体在一个渠道、一个币种下与渠道结算的边界；平台流水与渠道账单都挂在它下面。</li>
 * <li>每个 MID + 币种只能归属一个清算账户；挂 MID 前在事务外向 channel-connector 核对：MID 存在、ACTIVE、渠道与经营主体和清算账户一致、
 * 能力组含该币种。</li>
 * <li>运营写命令的操作人取自 {@link ReconciliationHeaders#ACTOR_HEADER} / {@link ReconciliationHeaders#SESSION_HEADER}，仅经 Operations
 * BFF 转发（照 ledger 人工调整）；查询不带。</li>
 */
@HttpExchange(url = "/reconciliation/v1/clearing-accounts", accept = "application/json")
public interface ClearingAccountApi {

    /**
     * 新建清算账户（ACTIVE、无 MID）
     *
     * @param actorAccountId 操作人 Account ID，正十进制整数字符串；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID；缺失时 400
     * @param body 编码、渠道、经营主体、币种、三项配置与到账银行账户
     * @return 201 与清算账户；编码已存在、到账银行账户不是同主体同币种 CHANNEL_SETTLEMENT 用途的 ACTIVE 账户时 400
     */
    @PostExchange
    ResponseEntity<ClearingAccountResponseBody> createClearingAccount(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @Valid @RequestBody CreateClearingAccountRequestBody body);

    /**
     * 修改清算账户三项配置与到账银行账户（整体替换）
     *
     * @param actorAccountId 操作人 Account ID；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID；缺失时 400
     * @param clearingAccountId 清算账户主键
     * @param body 新配置
     * @return 200 与清算账户；清算账户不存在或到账银行账户不合格时 400；并发修改时 409
     */
    @PutExchange("/{clearingAccountId}/settings")
    ClearingAccountResponseBody updateClearingAccountSettings(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("clearingAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String clearingAccountId,
        @Valid @RequestBody UpdateClearingAccountSettingsRequestBody body);

    /**
     * 向清算账户挂入 MID
     *
     * @param actorAccountId 操作人 Account ID；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID；缺失时 400
     * @param clearingAccountId 清算账户主键
     * @param body MID 主键
     * @return 200 与含全部 MID 的清算账户；清算账户停用、MID 不存在或非 ACTIVE、渠道或经营主体不一致、能力组无清算币种、
     *         MID + 币种已归属其他清算账户时 400；同一 MID 已在本账户时返回当前结果；channel-connector 不可用时 503
     */
    @PostExchange("/{clearingAccountId}/mids")
    ClearingAccountResponseBody addClearingAccountMid(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("clearingAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String clearingAccountId,
        @Valid @RequestBody AddClearingAccountMidRequestBody body);

    /**
     * 从清算账户摘除 MID
     * <li>摘除即删除归属行；已生成的平台流水仍属原清算账户。</li>
     *
     * @param actorAccountId 操作人 Account ID；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID；缺失时 400
     * @param clearingAccountId 清算账户主键
     * @param midId channel-connector MID 主键
     * @return 200 与含剩余 MID 的清算账户；清算账户不存在或 MID 不在本账户时 400
     */
    @DeleteExchange("/{clearingAccountId}/mids/{midId}")
    ClearingAccountResponseBody removeClearingAccountMid(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("clearingAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String clearingAccountId,
        @PathVariable("midId") @Pattern(regexp = "[1-9][0-9]{0,18}") String midId);

    /**
     * 启用或停用清算账户
     *
     * @param actorAccountId 操作人 Account ID；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID；缺失时 400
     * @param clearingAccountId 清算账户主键
     * @param body 目标状态
     * @return 200 与清算账户；已是目标状态时返回当前结果；清算账户不存在时 400
     */
    @PostExchange("/{clearingAccountId}/status")
    ClearingAccountResponseBody changeClearingAccountStatus(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("clearingAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String clearingAccountId,
        @Valid @RequestBody ChangeClearingAccountStatusRequestBody body);

    /**
     * 分页查询清算账户
     *
     * @param entityNo 可选经营主体编号
     * @param channelCode 可选渠道编码
     * @param currency 可选币种
     * @param status 可选状态：ACTIVE、INACTIVE
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按清算账户编码升序的分页结果；筛选或分页参数非法时 400
     */
    @GetExchange
    PagingResponseBody<ClearingAccountSummaryResponseBody> listClearingAccounts(
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam(value = "channelCode", required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String channelCode,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询清算账户详情
     *
     * @param clearingAccountId 清算账户主键
     * @return 清算账户与按挂入时间升序的 MID；不存在时 400
     */
    @GetExchange("/{clearingAccountId}")
    ClearingAccountResponseBody getClearingAccount(
        @PathVariable("clearingAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String clearingAccountId);

    /**
     * 查询已激活但尚未归入清算账户的 MID + 币种（UC-R17 提示）
     * <li>= channel-connector ACTIVE MID × 能力组币种 − 已归属；在事务外读取 channel-connector。</li>
     *
     * @param entityNo 可选经营主体编号
     * @param channelCode 可选渠道编码
     * @param currency 可选币种
     * @return 按渠道编码、经营主体、MID 主键、币种升序的未归属列表；channel-connector 不可用时 503
     */
    @GetExchange("/unassigned-mids")
    UnassignedMidListResponseBody listUnassignedMids(
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam(value = "channelCode", required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String channelCode,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency);
}
