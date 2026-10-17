package com.paytech.ledger.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.AccountingRuleBundleApi;
import com.paytech.ledger.api.request.CreateRuleVersionRequestBody;
import com.paytech.ledger.api.request.ReviewRuleVersionRequestBody;
import com.paytech.ledger.api.request.SubmitRuleVersionRequestBody;
import com.paytech.ledger.api.response.RuleBundleResponseBody;
import com.paytech.ledger.api.response.RuleVersionResponseBody;
import com.paytech.ledger.app.ext.rest.converter.AccountingRuleBundleConverter;
import com.paytech.ledger.domainservice.service.AccountingRuleService;
import com.paytech.ledger.service.processor.ApproveRuleVersionProcessor;
import com.paytech.ledger.service.processor.CreateRuleVersionProcessor;
import com.paytech.ledger.service.processor.RejectRuleVersionProcessor;
import com.paytech.ledger.service.processor.SubmitRuleVersionProcessor;

import lombok.AllArgsConstructor;

/** 自动记账规则 Bundle 的 HTTP 协议适配；查询直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class AccountingRuleBundleController implements AccountingRuleBundleApi {
    private final AccountingRuleService accountingRuleService;
    private final AccountingRuleBundleConverter converter;
    private final CreateRuleVersionProcessor createRuleVersionProcessor;
    private final RejectRuleVersionProcessor rejectRuleVersionProcessor;
    private final SubmitRuleVersionProcessor submitRuleVersionProcessor;
    private final ApproveRuleVersionProcessor approveRuleVersionProcessor;

    /** 创建规则版本并返回 201。 */
    @Override
    public ResponseEntity<RuleVersionResponseBody> createRuleVersion(String bundleCode, CreateRuleVersionRequestBody body) {
        RuleVersionResponseBody response =
            converter.toResponseBody(createRuleVersionProcessor.process(converter.toRequest(bundleCode, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 提交规则版本待复核。 */
    @Override
    public RuleVersionResponseBody submitRuleVersion(String bundleCode, String bundleVer, SubmitRuleVersionRequestBody body) {
        return converter.toResponseBody(submitRuleVersionProcessor.process(converter.toRequest(bundleCode, bundleVer, body)));
    }

    /** 批准规则版本。 */
    @Override
    public RuleVersionResponseBody approveRuleVersion(String bundleCode, String bundleVer, ReviewRuleVersionRequestBody body) {
        return converter.toResponseBody(approveRuleVersionProcessor.process(converter.toRequest(bundleCode, bundleVer, body)));
    }

    /** 驳回规则版本。 */
    @Override
    public RuleVersionResponseBody rejectRuleVersion(String bundleCode, String bundleVer, ReviewRuleVersionRequestBody body) {
        return converter.toResponseBody(rejectRuleVersionProcessor.process(converter.toRequest(bundleCode, bundleVer, body)));
    }

    /** 查询规则集与版本摘要。 */
    @Override
    public RuleBundleResponseBody getRuleBundle(String bundleCode) {
        return converter.toResponseBody(accountingRuleService.getBundle(bundleCode));
    }

    /** 查询规则版本详情。 */
    @Override
    public RuleVersionResponseBody getRuleVersion(String bundleCode, String bundleVer) {
        return converter.toResponseBody(accountingRuleService.getVersion(bundleCode, Long.parseLong(bundleVer)));
    }
}
