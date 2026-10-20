package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.CreateRuleVersionRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.RuleBundleListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.RuleBundleResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.RuleVersionResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * ledger 自动记账规则包的本地端口；规则校验、版本生命周期与生效切换归 ledger
 * <li>命令以会话 Account ID 与 Session ID 作为操作人 Header 转发；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface FinanceRuleBundleClient {

    /**
     * 全部规则包
     *
     * @return 规则包及当前生效版本
     */
    RuleBundleListResponseBody listRuleBundles();

    /**
     * 规则包详情
     *
     * @param bundleCode 规则包代码
     * @return 规则包与全部版本摘要
     */
    RuleBundleResponseBody getRuleBundle(String bundleCode);

    /**
     * 规则版本详情
     *
     * @param bundleCode 规则包代码
     * @param bundleVer 版本号
     * @return 版本详情与规则行
     */
    RuleVersionResponseBody getRuleVersion(String bundleCode, String bundleVer);

    /**
     * 新建规则版本（DRAFT）
     *
     * @param context 编制人会话
     * @param bundleCode 规则包代码
     * @param request 生效时间与完整规则行集
     * @return DRAFT 版本详情
     */
    RuleVersionResponseBody createRuleVersion(OperatorContext context, String bundleCode,
        CreateRuleVersionRequestBody request);

    /**
     * 提交规则版本
     *
     * @param context 提交人会话（必须是编制人）
     * @param bundleCode 规则包代码
     * @param bundleVer 版本号
     * @return PENDING_APPROVAL 版本详情
     */
    RuleVersionResponseBody submitRuleVersion(OperatorContext context, String bundleCode, String bundleVer);

    /**
     * 批准规则版本
     *
     * @param context 复核人会话
     * @param bundleCode 规则包代码
     * @param bundleVer 版本号
     * @return ACTIVE 或 APPROVED 版本详情
     */
    RuleVersionResponseBody approveRuleVersion(OperatorContext context, String bundleCode, String bundleVer);

    /**
     * 驳回规则版本
     *
     * @param context 复核人会话
     * @param bundleCode 规则包代码
     * @param bundleVer 版本号
     * @return REJECTED 版本详情
     */
    RuleVersionResponseBody rejectRuleVersion(OperatorContext context, String bundleCode, String bundleVer);

    /**
     * 作废规则草稿
     *
     * @param context 操作人会话（必须是编制人）
     * @param bundleCode 规则包代码
     * @param bundleVer 版本号
     * @return VOIDED 版本详情
     */
    RuleVersionResponseBody voidRuleVersion(OperatorContext context, String bundleCode, String bundleVer);
}
