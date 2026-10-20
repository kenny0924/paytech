package com.paytech.ledger.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.AccountingRuleBundleApi;
import com.paytech.ledger.api.request.CreateRuleVersionRequestBody;
import com.paytech.ledger.api.response.RuleBundleListResponseBody;
import com.paytech.ledger.api.response.RuleBundleResponseBody;
import com.paytech.ledger.api.response.RuleVersionResponseBody;
import com.paytech.ledger.app.ext.rest.converter.AccountingRuleBundleConverter;
import com.paytech.ledger.domainservice.service.AccountingRuleService;
import com.paytech.ledger.service.processor.ApproveRuleVersionProcessor;
import com.paytech.ledger.service.processor.CreateRuleVersionProcessor;
import com.paytech.ledger.service.processor.RejectRuleVersionProcessor;
import com.paytech.ledger.service.processor.SubmitRuleVersionProcessor;
import com.paytech.ledger.service.processor.VoidRuleVersionProcessor;

import lombok.AllArgsConstructor;

/** 自动记账规则 Bundle 的 HTTP 协议适配：操作人 Header 作为编制人 / 提交人 / 复核人传入 Processor；查询直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class AccountingRuleBundleController implements AccountingRuleBundleApi {
    private final AccountingRuleService accountingRuleService;
    private final AccountingRuleBundleConverter converter;
    private final VoidRuleVersionProcessor voidRuleVersionProcessor;
    private final CreateRuleVersionProcessor createRuleVersionProcessor;
    private final RejectRuleVersionProcessor rejectRuleVersionProcessor;
    private final SubmitRuleVersionProcessor submitRuleVersionProcessor;
    private final ApproveRuleVersionProcessor approveRuleVersionProcessor;

    /** 创建规则版本并返回 201。 */
    @Override
    public ResponseEntity<RuleVersionResponseBody> createRuleVersion(String actorAccountId, String sessionId,
        String bundleCode, CreateRuleVersionRequestBody body) {
        RuleVersionResponseBody response = converter.toResponseBody(
            createRuleVersionProcessor.process(converter.toRequest(bundleCode, body, actorAccountId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 提交规则版本待复核。 */
    @Override
    public RuleVersionResponseBody submitRuleVersion(String actorAccountId, String sessionId, String bundleCode,
        String bundleVer) {
        return converter.toResponseBody(
            submitRuleVersionProcessor.process(converter.toSubmitRequest(bundleCode, bundleVer, actorAccountId)));
    }

    /** 批准规则版本。 */
    @Override
    public RuleVersionResponseBody approveRuleVersion(String actorAccountId, String sessionId, String bundleCode,
        String bundleVer) {
        return converter.toResponseBody(
            approveRuleVersionProcessor.process(converter.toReviewRequest(bundleCode, bundleVer, actorAccountId)));
    }

    /** 驳回规则版本。 */
    @Override
    public RuleVersionResponseBody rejectRuleVersion(String actorAccountId, String sessionId, String bundleCode,
        String bundleVer) {
        return converter.toResponseBody(
            rejectRuleVersionProcessor.process(converter.toReviewRequest(bundleCode, bundleVer, actorAccountId)));
    }

    /** 作废规则草稿。 */
    @Override
    public RuleVersionResponseBody voidRuleVersion(String actorAccountId, String sessionId, String bundleCode,
        String bundleVer) {
        return converter.toResponseBody(
            voidRuleVersionProcessor.process(converter.toVoidRequest(bundleCode, bundleVer, actorAccountId)));
    }

    /** 查询全部规则集。 */
    @Override
    public RuleBundleListResponseBody listRuleBundles() {
        return converter.toListBody(accountingRuleService.listBundles());
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
