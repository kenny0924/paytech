package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.CreateRuleVersionRequestBody;
import com.paytech.ledger.api.response.RuleBundleListResponseBody;
import com.paytech.ledger.api.response.RuleBundleResponseBody;
import com.paytech.ledger.api.response.RuleVersionResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 交易自动记账规则 Bundle 版本管理（Maker-Checker）
 * <li>Bundle 只由初始化 DML 创建；版本以完整有序规则行集创建为 DRAFT，提交后冻结，Checker（≠ Maker）批准或驳回；DRAFT 可由编制人作废（VOIDED）。</li>
 * <li>批准时已到生效时间则生效时间记为批准时间、成为 ACTIVE 并原子切换当前版本，原当前版本 RETIRED；未到生效时间保持 APPROVED，到期由 XXL-Job
 * accountingRuleVersionActivation 切换为 ACTIVE。</li>
 * <li>Finance 内部接口：编制人与复核人取自 Operations BFF 从会话填入的 X-Gateway-Actor-Subject（Operations Account ID）/
 * X-Gateway-Session-Id Header，仅经 Operations BFF 转发，不得直接对外发布。</li>
 * <li>需要页面区分的拒绝以 {@link com.paytech.ledger.api.enums.LedgerErrorCode} 返回 400：RULE_VERSION_IN_PROGRESS、RULE_VERSION_STATUS_INVALID、
 * RULE_SUBMITTER_NOT_MAKER、RULE_LINE_INVALID、RULE_EFFECTIVE_FROM_INVALID、SELF_REVIEW_FORBIDDEN；其余为 BUSINESS_CHECK_FAILED。</li>
 */
@HttpExchange(url = "/accounting-rule-bundles/v1", accept = "application/json")
public interface AccountingRuleBundleApi {

    /**
     * 创建规则版本（DRAFT）
     *
     * @param actorAccountId 编制人 Account ID（X-Gateway-Actor-Subject），正十进制整数字符串；缺失或格式错误时 400
     * @param sessionId 编制人会话 ID（X-Gateway-Session-Id）；缺失时 400
     * @param bundleCode 规则集代码
     * @param body 生效时间与完整规则行集
     * @return 201 与版本详情，版本号为当前最大值 + 1；规则集不存在、代码/科目/余额桶非法、行号或行代码重复、
     *         已有 DRAFT 或 PENDING_APPROVAL 版本（VOIDED 不计）时 400；并发写入时 409
     */
    @PostExchange("/{bundleCode}/versions")
    ResponseEntity<RuleVersionResponseBody> createRuleVersion(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @Valid @RequestBody CreateRuleVersionRequestBody body);

    /**
     * 提交规则版本待复核，冻结规则行集
     *
     * @param actorAccountId 提交人 Account ID（X-Gateway-Actor-Subject），必须等于编制人；缺失或格式错误时 400
     * @param sessionId 提交人会话 ID（X-Gateway-Session-Id）；缺失时 400
     * @param bundleCode 规则集代码
     * @param bundleVer 版本号
     * @return 200 与 PENDING_APPROVAL 版本详情；非 DRAFT、提交人不是编制人或某指令类型缺少借方或贷方时 400
     */
    @PostExchange("/{bundleCode}/versions/{bundleVer}/submit")
    RuleVersionResponseBody submitRuleVersion(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = "[1-9][0-9]{0,18}") String bundleVer);

    /**
     * 批准规则版本
     *
     * @param actorAccountId 复核人 Account ID（X-Gateway-Actor-Subject），必须不同于编制人；缺失或格式错误时 400
     * @param sessionId 复核人会话 ID（X-Gateway-Session-Id）；缺失时 400
     * @param bundleCode 规则集代码
     * @param bundleVer 版本号
     * @return 200 与版本详情：已到生效时间为 ACTIVE（生效时间记为批准时间，当前版本切换），否则 APPROVED；非 PENDING_APPROVAL 或同人复核时 400
     */
    @PostExchange("/{bundleCode}/versions/{bundleVer}/approve")
    RuleVersionResponseBody approveRuleVersion(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = "[1-9][0-9]{0,18}") String bundleVer);

    /**
     * 驳回规则版本
     *
     * @param actorAccountId 复核人 Account ID（X-Gateway-Actor-Subject），必须不同于编制人；缺失或格式错误时 400
     * @param sessionId 复核人会话 ID（X-Gateway-Session-Id）；缺失时 400
     * @param bundleCode 规则集代码
     * @param bundleVer 版本号
     * @return 200 与 REJECTED 版本详情；非 PENDING_APPROVAL 或同人复核时 400
     */
    @PostExchange("/{bundleCode}/versions/{bundleVer}/reject")
    RuleVersionResponseBody rejectRuleVersion(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = "[1-9][0-9]{0,18}") String bundleVer);

    /**
     * 作废规则草稿：DRAFT → VOIDED，不再可提交
     *
     * @param actorAccountId 操作人 Account ID（X-Gateway-Actor-Subject），必须是编制人；缺失或格式错误时 400
     * @param sessionId 操作人会话 ID（X-Gateway-Session-Id）；缺失时 400
     * @param bundleCode 规则集代码
     * @param bundleVer 版本号
     * @return 200 与 VOIDED 版本详情；编制人重复作废直接返回当前结果；规则集或版本不存在、非编制人、版本不是 DRAFT 时 400
     */
    @PostExchange("/{bundleCode}/versions/{bundleVer}/void")
    RuleVersionResponseBody voidRuleVersion(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = "[1-9][0-9]{0,18}") String bundleVer);

    /**
     * 查询全部规则集
     *
     * @return 按规则集代码升序的规则集及当前生效版本
     */
    @GetExchange
    RuleBundleListResponseBody listRuleBundles();

    /**
     * 查询规则集
     *
     * @param bundleCode 规则集代码
     * @return 规则集状态、当前版本号与全部版本摘要（版本号降序）；规则集不存在时 400
     */
    @GetExchange("/{bundleCode}")
    RuleBundleResponseBody getRuleBundle(
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode);

    /**
     * 查询规则版本详情
     *
     * @param bundleCode 规则集代码
     * @param bundleVer 版本号
     * @return 版本详情及按指令类型、行号排序的规则行；规则集或版本不存在时 400
     */
    @GetExchange("/{bundleCode}/versions/{bundleVer}")
    RuleVersionResponseBody getRuleVersion(
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = "[1-9][0-9]{0,18}") String bundleVer);
}
