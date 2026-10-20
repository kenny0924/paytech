package com.paytech.ledger.app.ext.rest.converter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.ledger.api.request.CreateRuleVersionRequestBody;
import com.paytech.ledger.api.request.RuleLineRequestBody;
import com.paytech.ledger.api.response.RuleBundleListResponseBody;
import com.paytech.ledger.api.response.RuleBundleResponseBody;
import com.paytech.ledger.api.response.RuleVersionResponseBody;
import com.paytech.ledger.domain.view.RuleBundleView;
import com.paytech.ledger.domain.view.RuleVersionView;
import com.paytech.ledger.service.processor.request.CreateRuleVersionRequest;
import com.paytech.ledger.service.processor.request.ReviewRuleVersionRequest;
import com.paytech.ledger.service.processor.request.SubmitRuleVersionRequest;
import com.paytech.ledger.service.processor.request.VoidRuleVersionRequest;

/**
 * 自动记账规则接口的 Wire 模型与内部请求/View 转换；ID 与版本号输出为十进制字符串，操作人取自 Header（已由接口校验为正十进制）
 * <li>规则行代码保持字符串传入，由 Processor 按固定目录解析并以业务错误拒绝非法值。</li>
 */
@Component
public class AccountingRuleBundleConverter {

    /** 转换创建请求，编制人取自操作人 Header。 */
    public CreateRuleVersionRequest toRequest(String bundleCode, CreateRuleVersionRequestBody body, String operatorId) {
        CreateRuleVersionRequest request = new CreateRuleVersionRequest();
        request.setBundleCode(bundleCode);
        request.setMakerId(Long.valueOf(operatorId));
        request.setEffectiveFrom(body.getEffectiveFrom());
        request.setLines(body.getLines().stream().map(this::toLine).toList());
        return request;
    }

    /** 转换规则行。 */
    CreateRuleVersionRequest.Line toLine(RuleLineRequestBody body) {
        CreateRuleVersionRequest.Line line = new CreateRuleVersionRequest.Line();
        line.setInstructionType(body.getInstructionType());
        line.setLineNo(body.getLineNo());
        line.setLineCode(body.getLineCode());
        line.setAmountSourceCode(body.getAmountSourceCode());
        line.setOwnerRole(body.getOwnerRole());
        line.setAccountCode(body.getAccountCode());
        line.setBalanceBucket(body.getBalanceBucket());
        line.setEntrySide(body.getEntrySide());
        line.setRequiredFlag(body.getRequiredFlag());
        return line;
    }

    /** 转换提交请求，提交人取自操作人 Header。 */
    public SubmitRuleVersionRequest toSubmitRequest(String bundleCode, String bundleVer, String operatorId) {
        SubmitRuleVersionRequest request = new SubmitRuleVersionRequest();
        request.setBundleCode(bundleCode);
        request.setBundleVer(Long.valueOf(bundleVer));
        request.setMakerId(Long.valueOf(operatorId));
        return request;
    }

    /** 转换复核请求，复核人取自操作人 Header。 */
    public ReviewRuleVersionRequest toReviewRequest(String bundleCode, String bundleVer, String operatorId) {
        ReviewRuleVersionRequest request = new ReviewRuleVersionRequest();
        request.setBundleCode(bundleCode);
        request.setBundleVer(Long.valueOf(bundleVer));
        request.setCheckerId(Long.valueOf(operatorId));
        return request;
    }

    /** 转换作废请求，操作人取自 Header。 */
    public VoidRuleVersionRequest toVoidRequest(String bundleCode, String bundleVer, String operatorId) {
        VoidRuleVersionRequest request = new VoidRuleVersionRequest();
        request.setBundleCode(bundleCode);
        request.setBundleVer(Long.valueOf(bundleVer));
        request.setMakerId(Long.valueOf(operatorId));
        return request;
    }

    /** 转换版本详情。 */
    public RuleVersionResponseBody toResponseBody(RuleVersionView view) {
        RuleVersionResponseBody body = new RuleVersionResponseBody();
        body.setVersionId(view.getVersionId().toString());
        body.setBundleCode(view.getBundleCode());
        body.setBundleVer(view.getBundleVer().toString());
        body.setEffectiveFrom(view.getEffectiveFrom());
        body.setEffectiveTo(view.getEffectiveTo());
        body.setStatus(view.getStatus().getCode());
        body.setMakerId(view.getMakerId().toString());
        body.setCheckerId(text(view.getCheckerId()));
        body.setApprovedTime(view.getApprovedTime());
        body.setLines(view.getLines().stream().map(this::toLineBody).toList());
        return body;
    }

    /** 转换规则行。 */
    RuleVersionResponseBody.Line toLineBody(RuleVersionView.Line line) {
        RuleVersionResponseBody.Line body = new RuleVersionResponseBody.Line();
        body.setRuleLineId(line.getRuleLineId().toString());
        body.setInstructionType(line.getInstructionType().getCode());
        body.setLineNo(line.getLineNo());
        body.setLineCode(line.getLineCode());
        body.setAmountSourceCode(line.getAmountSourceCode().getCode());
        body.setOwnerRole(line.getOwnerRole().getCode());
        body.setAccountCode(line.getAccountCode());
        body.setBalanceBucket(line.getBalanceBucket().getCode());
        body.setEntrySide(line.getEntrySide().getCode());
        body.setRequiredFlag(line.getRequiredFlag());
        return body;
    }

    /** 转换规则集与版本摘要。 */
    public RuleBundleResponseBody toResponseBody(RuleBundleView view) {
        RuleBundleResponseBody body = new RuleBundleResponseBody();
        body.setBundleId(view.getBundleId().toString());
        body.setBundleCode(view.getBundleCode());
        body.setStatus(view.getStatus().getCode());
        body.setCurrentBundleVer(text(view.getCurrentBundleVer()));
        body.setVersions(view.getVersions().stream().map(this::toVersionBody).toList());
        return body;
    }

    /** 转换全部规则集；当前生效版本的开始时间取自版本摘要。 */
    public RuleBundleListResponseBody toListBody(List<RuleBundleView> views) {
        RuleBundleListResponseBody body = new RuleBundleListResponseBody();
        body.setItems(views.stream().map(this::toBundleItem).toList());
        return body;
    }

    /** 转换一个规则集及其当前生效版本。 */
    RuleBundleListResponseBody.Bundle toBundleItem(RuleBundleView view) {
        RuleBundleListResponseBody.Bundle item = new RuleBundleListResponseBody.Bundle();
        item.setBundleCode(view.getBundleCode());
        item.setStatus(view.getStatus().getCode());
        item.setCurrentBundleVer(text(view.getCurrentBundleVer()));
        item.setCurrentEffectiveFrom(view.getVersions().stream()
            .filter(version -> version.getBundleVer().equals(view.getCurrentBundleVer()))
            .map(RuleBundleView.Version::getEffectiveFrom).findFirst().orElse(null));
        return item;
    }

    /** 转换版本摘要。 */
    RuleBundleResponseBody.Version toVersionBody(RuleBundleView.Version version) {
        RuleBundleResponseBody.Version body = new RuleBundleResponseBody.Version();
        body.setVersionId(version.getVersionId().toString());
        body.setBundleVer(version.getBundleVer().toString());
        body.setStatus(version.getStatus().getCode());
        body.setEffectiveFrom(version.getEffectiveFrom());
        body.setEffectiveTo(version.getEffectiveTo());
        return body;
    }

    /** 可空 Long 转十进制字符串。 */
    String text(Long value) {
        return value == null ? null : value.toString();
    }
}
