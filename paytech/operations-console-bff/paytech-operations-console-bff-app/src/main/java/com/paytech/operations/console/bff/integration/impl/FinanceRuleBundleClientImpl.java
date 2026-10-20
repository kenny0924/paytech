package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.ledger.api.AccountingRuleBundleApi;
import com.paytech.operations.console.bff.integration.FinanceRuleBundleClient;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.CreateRuleVersionRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.RuleBundleListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.RuleBundleResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.RuleVersionResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * ledger 自动记账规则包的 HTTP 转发：每个入口只调用一次 ledger，不重试
 * <li>命令把会话 Account ID 与 Session ID 填入操作人 Header；提交、批准、驳回与作废无请求体。</li>
 * <li>规则包与版本回执的规则包代码、版本号须与请求一致；规则行与版本列表不可缺失。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class FinanceRuleBundleClientImpl implements FinanceRuleBundleClient {
    private final AccountingRuleBundleApi api;

    /** 全部规则包。 */
    @Override
    public RuleBundleListResponseBody listRuleBundles() {
        var bundles = convert(api.listRuleBundles(), RuleBundleListResponseBody.class);
        FinanceContractConverter.require(bundles.getItems() != null);
        return bundles;
    }

    /** 规则包详情；代码须与请求一致。 */
    @Override
    public RuleBundleResponseBody getRuleBundle(String bundleCode) {
        var bundle = convert(api.getRuleBundle(bundleCode), RuleBundleResponseBody.class);
        requireSame(bundleCode, bundle.getBundleCode());
        FinanceContractConverter.require(bundle.getVersions() != null);
        return bundle;
    }

    /** 版本详情。 */
    @Override
    public RuleVersionResponseBody getRuleVersion(String bundleCode, String bundleVer) {
        return checked(bundleCode, bundleVer, api.getRuleVersion(bundleCode, bundleVer));
    }

    /** 新建版本；回执须属于请求规则包并带新版本号。 */
    @Override
    public RuleVersionResponseBody createRuleVersion(OperatorContext context, String bundleCode,
        CreateRuleVersionRequestBody request) {
        var created = write(request, () -> api.createRuleVersion(context.accountId(), context.sessionId(), bundleCode,
            convert(request, com.paytech.ledger.api.request.CreateRuleVersionRequestBody.class)).getBody(),
            RuleVersionResponseBody.class);
        requireSame(bundleCode, created.getBundleCode());
        requireIdentity(created.getBundleVer());
        FinanceContractConverter.require(created.getLines() != null);
        return created;
    }

    /** 提交版本，无请求体。 */
    @Override
    public RuleVersionResponseBody submitRuleVersion(OperatorContext context, String bundleCode, String bundleVer) {
        return checked(bundleCode, bundleVer,
            api.submitRuleVersion(context.accountId(), context.sessionId(), bundleCode, bundleVer));
    }

    /** 批准版本，无请求体。 */
    @Override
    public RuleVersionResponseBody approveRuleVersion(OperatorContext context, String bundleCode, String bundleVer) {
        return checked(bundleCode, bundleVer,
            api.approveRuleVersion(context.accountId(), context.sessionId(), bundleCode, bundleVer));
    }

    /** 驳回版本，无请求体。 */
    @Override
    public RuleVersionResponseBody rejectRuleVersion(OperatorContext context, String bundleCode, String bundleVer) {
        return checked(bundleCode, bundleVer,
            api.rejectRuleVersion(context.accountId(), context.sessionId(), bundleCode, bundleVer));
    }

    /** 作废草稿，无请求体。 */
    @Override
    public RuleVersionResponseBody voidRuleVersion(OperatorContext context, String bundleCode, String bundleVer) {
        return checked(bundleCode, bundleVer,
            api.voidRuleVersion(context.accountId(), context.sessionId(), bundleCode, bundleVer));
    }

    /** 转换版本并核对规则包代码、版本号与规则行。 */
    private static RuleVersionResponseBody checked(String bundleCode, String bundleVer, Object value) {
        var version = convert(value, RuleVersionResponseBody.class);
        requireSame(bundleCode, version.getBundleCode());
        requireSame(bundleVer, version.getBundleVer());
        FinanceContractConverter.require(version.getLines() != null);
        return version;
    }
}
