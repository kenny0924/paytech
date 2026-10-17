package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.CreateRuleVersionRequestBody;
import com.paytech.ledger.api.request.ReviewRuleVersionRequestBody;
import com.paytech.ledger.api.request.SubmitRuleVersionRequestBody;
import com.paytech.ledger.api.response.RuleBundleResponseBody;
import com.paytech.ledger.api.response.RuleVersionResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 交易自动记账规则 Bundle 版本管理（Maker-Checker）
 * <li>Bundle 只由初始化 DML 创建；版本以完整有序规则行集创建为 DRAFT，提交后冻结，Checker（≠ Maker）批准或驳回。</li>
 * <li>批准时已到生效时间则成为 ACTIVE 并原子切换当前版本，原当前版本 RETIRED；未到生效时间保持 APPROVED。</li>
 * <li>Finance 内部开发接口：操作人标识由调用方传入，未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/accounting-rule-bundles/v1", accept = "application/json")
public interface AccountingRuleBundleApi {

    /**
     * 创建规则版本（DRAFT）
     *
     * @param bundleCode 规则集代码
     * @param body 编制人、生效时间与完整规则行集
     * @return 201 与版本详情，版本号为当前最大值 + 1；规则集不存在、代码/科目/余额桶非法、行号或行代码重复、
     *         已有 DRAFT 或 PENDING_APPROVAL 版本时 400；并发写入时 409
     */
    @PostExchange("/{bundleCode}/versions")
    ResponseEntity<RuleVersionResponseBody> createRuleVersion(
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @Valid @RequestBody CreateRuleVersionRequestBody body);

    /**
     * 提交规则版本待复核，冻结规则行集
     *
     * @param bundleCode 规则集代码
     * @param bundleVer 版本号
     * @param body 提交人，必须等于编制人
     * @return 200 与 PENDING_APPROVAL 版本详情；非 DRAFT、提交人不是编制人或某指令类型缺少借方或贷方时 400
     */
    @PostExchange("/{bundleCode}/versions/{bundleVer}/submit")
    RuleVersionResponseBody submitRuleVersion(
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = "[1-9][0-9]{0,18}") String bundleVer,
        @Valid @RequestBody SubmitRuleVersionRequestBody body);

    /**
     * 批准规则版本
     *
     * @param bundleCode 规则集代码
     * @param bundleVer 版本号
     * @param body 复核人，必须不同于编制人
     * @return 200 与版本详情：已到生效时间为 ACTIVE（当前版本切换），否则 APPROVED；非 PENDING_APPROVAL 或同人复核时 400
     */
    @PostExchange("/{bundleCode}/versions/{bundleVer}/approve")
    RuleVersionResponseBody approveRuleVersion(
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = "[1-9][0-9]{0,18}") String bundleVer,
        @Valid @RequestBody ReviewRuleVersionRequestBody body);

    /**
     * 驳回规则版本
     *
     * @param bundleCode 规则集代码
     * @param bundleVer 版本号
     * @param body 复核人，必须不同于编制人
     * @return 200 与 REJECTED 版本详情；非 PENDING_APPROVAL 或同人复核时 400
     */
    @PostExchange("/{bundleCode}/versions/{bundleVer}/reject")
    RuleVersionResponseBody rejectRuleVersion(
        @PathVariable("bundleCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = "[1-9][0-9]{0,18}") String bundleVer,
        @Valid @RequestBody ReviewRuleVersionRequestBody body);

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
