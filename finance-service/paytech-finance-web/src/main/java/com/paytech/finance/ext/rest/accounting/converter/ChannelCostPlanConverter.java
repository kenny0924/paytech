package com.paytech.finance.ext.rest.accounting.converter;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.paytech.finance.accounting.domain.view.ChannelCostPlanView;
import com.paytech.finance.accounting.domain.view.ChannelCostVersionView;
import com.paytech.finance.accounting.service.processor.request.CreateChannelCostPlanRequest;
import com.paytech.finance.accounting.service.processor.request.CreateChannelCostVersionRequest;
import com.paytech.finance.accounting.service.processor.request.ReviewChannelCostVersionRequest;
import com.paytech.finance.accounting.service.processor.request.SubmitChannelCostVersionRequest;
import com.paytech.finance.api.accounting.request.ChannelCostComponentRequestBody;
import com.paytech.finance.api.accounting.request.ChannelCostScopeRequestBody;
import com.paytech.finance.api.accounting.request.CreateChannelCostPlanRequestBody;
import com.paytech.finance.api.accounting.request.CreateChannelCostVersionRequestBody;
import com.paytech.finance.api.accounting.request.ReviewChannelCostVersionRequestBody;
import com.paytech.finance.api.accounting.request.SubmitChannelCostVersionRequestBody;
import com.paytech.finance.api.accounting.response.ChannelCostComponentResponseBody;
import com.paytech.finance.api.accounting.response.ChannelCostPlanResponseBody;
import com.paytech.finance.api.accounting.response.ChannelCostScopeResponseBody;
import com.paytech.finance.api.accounting.response.ChannelCostVersionResponseBody;
import com.paytech.finance.api.accounting.response.ChannelCostVersionSummaryResponseBody;

/**
 * 渠道成本接口的 Wire 模型与内部请求/View 转换；ID 与版本号输出为十进制字符串，费率与金额字符串与 BigDecimal 精确互转
 * <li>组成类型、计算基础与舍入方式保持字符串传入，由 Processor 按 V1 目录解析并以参数错误拒绝非法值。</li>
 */
@Component
public class ChannelCostPlanConverter {

    /** 转换创建方案请求。 */
    public CreateChannelCostPlanRequest toRequest(CreateChannelCostPlanRequestBody body) {
        CreateChannelCostPlanRequest request = new CreateChannelCostPlanRequest();
        request.setChannelId(Long.valueOf(body.getChannelId()));
        request.setPlanCode(body.getPlanCode());
        return request;
    }

    /** 转换创建版本请求。 */
    public CreateChannelCostVersionRequest toRequest(String planCode, CreateChannelCostVersionRequestBody body) {
        CreateChannelCostVersionRequest request = new CreateChannelCostVersionRequest();
        request.setPlanCode(planCode);
        request.setMakerId(Long.valueOf(body.getMakerId()));
        request.setEffectiveFrom(body.getEffectiveFrom());
        request.setScopes(body.getScopes().stream().map(this::toScope).toList());
        return request;
    }

    /** 转换适用范围。 */
    CreateChannelCostVersionRequest.Scope toScope(ChannelCostScopeRequestBody body) {
        CreateChannelCostVersionRequest.Scope scope = new CreateChannelCostVersionRequest.Scope();
        scope.setScopeNo(body.getScopeNo());
        scope.setMidId(Long.valueOf(body.getMidId()));
        scope.setEntityNo(body.getEntityNo());
        scope.setChannelAgreementRef(body.getChannelAgreementRef());
        scope.setCountryCode(body.getCountryCode());
        scope.setCurrency(body.getCurrency());
        scope.setPaymentMethodCode(body.getPaymentMethodCode());
        scope.setActionType(body.getActionType());
        scope.setComponents(body.getComponents().stream().map(this::toComponent).toList());
        return scope;
    }

    /** 转换成本组成。 */
    CreateChannelCostVersionRequest.Component toComponent(ChannelCostComponentRequestBody body) {
        CreateChannelCostVersionRequest.Component component = new CreateChannelCostVersionRequest.Component();
        component.setComponentCode(body.getComponentCode());
        component.setComponentType(body.getComponentType());
        component.setCalculationBase(body.getCalculationBase());
        component.setRateValue(decimal(body.getRateValue()));
        component.setFixedAmount(decimal(body.getFixedAmount()));
        component.setCurrency(body.getCurrency());
        component.setMinAmount(decimal(body.getMinAmount()));
        component.setMaxAmount(decimal(body.getMaxAmount()));
        component.setRoundingMode(body.getRoundingMode());
        return component;
    }

    /** 转换提交请求。 */
    public SubmitChannelCostVersionRequest toRequest(String planCode, String costVer,
        SubmitChannelCostVersionRequestBody body) {
        SubmitChannelCostVersionRequest request = new SubmitChannelCostVersionRequest();
        request.setPlanCode(planCode);
        request.setCostVer(Integer.valueOf(costVer));
        request.setMakerId(Long.valueOf(body.getMakerId()));
        return request;
    }

    /** 转换复核请求。 */
    public ReviewChannelCostVersionRequest toRequest(String planCode, String costVer,
        ReviewChannelCostVersionRequestBody body) {
        ReviewChannelCostVersionRequest request = new ReviewChannelCostVersionRequest();
        request.setPlanCode(planCode);
        request.setCostVer(Integer.valueOf(costVer));
        request.setCheckerId(Long.valueOf(body.getCheckerId()));
        request.setApprovedContentHash(body.getApprovedContentHash());
        return request;
    }

    /** 转换方案与版本摘要。 */
    public ChannelCostPlanResponseBody toResponseBody(ChannelCostPlanView view) {
        ChannelCostPlanResponseBody body = new ChannelCostPlanResponseBody();
        body.setPlanCode(view.getPlanCode());
        body.setChannelId(view.getChannelId().toString());
        body.setStatus(view.getStatus().getCode());
        body.setCurrentCostVer(text(view.getCurrentCostVer()));
        body.setVersions(view.getVersions().stream().map(this::toSummaryBody).toList());
        return body;
    }

    /** 转换版本摘要。 */
    ChannelCostVersionSummaryResponseBody toSummaryBody(ChannelCostPlanView.Version version) {
        ChannelCostVersionSummaryResponseBody body = new ChannelCostVersionSummaryResponseBody();
        body.setCostVer(version.getCostVer().toString());
        body.setStatus(version.getStatus().getCode());
        body.setEffectiveFrom(version.getEffectiveFrom());
        body.setEffectiveTo(version.getEffectiveTo());
        body.setContentHash(version.getContentHash());
        body.setMakerId(version.getMakerId().toString());
        body.setCheckerId(text(version.getCheckerId()));
        return body;
    }

    /** 转换版本详情。 */
    public ChannelCostVersionResponseBody toResponseBody(ChannelCostVersionView view) {
        ChannelCostVersionResponseBody body = new ChannelCostVersionResponseBody();
        body.setPlanCode(view.getPlanCode());
        body.setCostVer(view.getCostVer().toString());
        body.setStatus(view.getStatus().getCode());
        body.setEffectiveFrom(view.getEffectiveFrom());
        body.setEffectiveTo(view.getEffectiveTo());
        body.setContentHash(view.getContentHash());
        body.setMakerId(view.getMakerId().toString());
        body.setCheckerId(text(view.getCheckerId()));
        body.setScopes(view.getScopes().stream().map(this::toScopeBody).toList());
        return body;
    }

    /** 转换适用范围。 */
    ChannelCostScopeResponseBody toScopeBody(ChannelCostVersionView.Scope scope) {
        ChannelCostScopeResponseBody body = new ChannelCostScopeResponseBody();
        body.setScopeNo(scope.getScopeNo());
        body.setMidId(scope.getMidId().toString());
        body.setEntityNo(scope.getEntityNo());
        body.setChannelAgreementRef(scope.getChannelAgreementRef());
        body.setCountryCode(scope.getCountryCode());
        body.setCurrency(scope.getCurrency());
        body.setPaymentMethodCode(scope.getPaymentMethodCode());
        body.setActionType(scope.getActionType());
        body.setComponents(scope.getComponents().stream().map(this::toComponentBody).toList());
        return body;
    }

    /** 转换成本组成；View 已规范费率与金额的小数位。 */
    ChannelCostComponentResponseBody toComponentBody(ChannelCostVersionView.Component component) {
        ChannelCostComponentResponseBody body = new ChannelCostComponentResponseBody();
        body.setComponentCode(component.getComponentCode());
        body.setComponentType(component.getComponentType().getCode());
        body.setCalculationBase(component.getCalculationBase().getCode());
        body.setRateValue(plain(component.getRateValue()));
        body.setFixedAmount(plain(component.getFixedAmount()));
        body.setCurrency(component.getCurrency());
        body.setMinAmount(plain(component.getMinAmount()));
        body.setMaxAmount(plain(component.getMaxAmount()));
        body.setRoundingMode(component.getRoundingMode().getCode());
        return body;
    }

    /** 可空十进制字符串转 BigDecimal。 */
    BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /** 可空 BigDecimal 转普通十进制字符串。 */
    String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    /** 可空数值转十进制字符串。 */
    String text(Number value) {
        return value == null ? null : value.toString();
    }
}
