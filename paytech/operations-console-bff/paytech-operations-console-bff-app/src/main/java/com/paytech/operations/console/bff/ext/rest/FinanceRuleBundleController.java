package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.FinanceRuleBundleClient;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.CreateRuleVersionRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.RuleBundleListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.RuleBundleResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceRuleBundleModels.RuleVersionResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 自动记账规则包的浏览器入口（块 102），全部简单转发 ledger AccountingRuleBundleApi
 * <li>Gateway 绑定：列表、详情 FINANCE_ACCOUNTING_READ；新建、提交、作废 FINANCE_ACCOUNTING_MANAGE；批准、驳回
 * FINANCE_ACCOUNTING_APPROVE，并经 PLT-I02 实时授权。</li>
 * <li>浏览器路径为 /finance + ledger 路径；提交、批准、驳回与作废不接受 Body。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/accounting-rule-bundles/v1")
public class FinanceRuleBundleController {
    private static final String BUNDLE_CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String BUNDLE_VER = "[1-9][0-9]{0,18}";

    private final FinanceRuleBundleClient client;

    /** 全部规则包及当前生效版本，按代码升序。 */
    @GetMapping
    public RuleBundleListResponseBody listRuleBundles(HttpServletRequest servletRequest) {
        requireQueryKeys(servletRequest, Set.of());
        return client.listRuleBundles();
    }

    /** 规则包详情与全部版本摘要（版本号降序）。 */
    @GetMapping("/{bundleCode}")
    public RuleBundleResponseBody getRuleBundle(HttpServletRequest servletRequest,
        @PathVariable("bundleCode") @Pattern(regexp = BUNDLE_CODE) String bundleCode) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getRuleBundle(bundleCode);
    }

    /** 规则版本详情与规则行。 */
    @GetMapping("/{bundleCode}/versions/{bundleVer}")
    public RuleVersionResponseBody getRuleVersion(HttpServletRequest servletRequest,
        @PathVariable("bundleCode") @Pattern(regexp = BUNDLE_CODE) String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = BUNDLE_VER) String bundleVer) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getRuleVersion(bundleCode, bundleVer);
    }

    /** 新建规则版本（DRAFT）。 */
    @PostMapping("/{bundleCode}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleVersionResponseBody createRuleVersion(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("bundleCode") @Pattern(regexp = BUNDLE_CODE) String bundleCode,
        @RequestBody @NotNull @Valid CreateRuleVersionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createRuleVersion(context, bundleCode, body);
    }

    /** 提交规则版本；无请求体。 */
    @PostMapping("/{bundleCode}/versions/{bundleVer}/submit")
    public RuleVersionResponseBody submitRuleVersion(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("bundleCode") @Pattern(regexp = BUNDLE_CODE) String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = BUNDLE_VER) String bundleVer) {
        requireQueryKeys(servletRequest, Set.of());
        return client.submitRuleVersion(context, bundleCode, bundleVer);
    }

    /** 批准规则版本；无请求体。 */
    @PostMapping("/{bundleCode}/versions/{bundleVer}/approve")
    public RuleVersionResponseBody approveRuleVersion(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("bundleCode") @Pattern(regexp = BUNDLE_CODE) String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = BUNDLE_VER) String bundleVer) {
        requireQueryKeys(servletRequest, Set.of());
        return client.approveRuleVersion(context, bundleCode, bundleVer);
    }

    /** 驳回规则版本；无请求体。 */
    @PostMapping("/{bundleCode}/versions/{bundleVer}/reject")
    public RuleVersionResponseBody rejectRuleVersion(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("bundleCode") @Pattern(regexp = BUNDLE_CODE) String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = BUNDLE_VER) String bundleVer) {
        requireQueryKeys(servletRequest, Set.of());
        return client.rejectRuleVersion(context, bundleCode, bundleVer);
    }

    /** 作废规则草稿；无请求体。 */
    @PostMapping("/{bundleCode}/versions/{bundleVer}/void")
    public RuleVersionResponseBody voidRuleVersion(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("bundleCode") @Pattern(regexp = BUNDLE_CODE) String bundleCode,
        @PathVariable("bundleVer") @Pattern(regexp = BUNDLE_VER) String bundleVer) {
        requireQueryKeys(servletRequest, Set.of());
        return client.voidRuleVersion(context, bundleCode, bundleVer);
    }
}
