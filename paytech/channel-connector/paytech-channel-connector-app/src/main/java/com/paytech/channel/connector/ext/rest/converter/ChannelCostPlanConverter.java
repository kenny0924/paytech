package com.paytech.channel.connector.ext.rest.converter;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.cost.request.CancelChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.ChannelCostComponentRequestBody;
import com.paytech.channel.connector.api.cost.request.ChannelCostScopeRequestBody;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostPlanRequestBody;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.ResolveChannelCostRequestBody;
import com.paytech.channel.connector.api.cost.request.ReviewChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.SubmitChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostComponentResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostPlanResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostPlanSummaryResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostResolutionResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostScopeResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostVersionResponseBody;
import com.paytech.channel.connector.api.cost.response.ChannelCostVersionSummaryResponseBody;
import com.paytech.channel.connector.core.enums.ChannelCostCodes;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.domain.ChannelCostPlanSummaryView;
import com.paytech.channel.connector.domain.ChannelCostPlanView;
import com.paytech.channel.connector.domain.ChannelCostResolutionView;
import com.paytech.channel.connector.domain.ChannelCostVersionView;
import com.paytech.channel.connector.service.processor.request.CancelChannelCostVersionRequest;
import com.paytech.channel.connector.service.processor.request.CreateChannelCostPlanRequest;
import com.paytech.channel.connector.service.processor.request.CreateChannelCostVersionRequest;
import com.paytech.channel.connector.service.processor.request.ResolveChannelCostRequest;
import com.paytech.channel.connector.service.processor.request.ReviewChannelCostVersionRequest;
import com.paytech.channel.connector.service.processor.request.SubmitChannelCostVersionRequest;

/**
 * 渠道成本接口的 Wire 模型与内部请求 / View 转换；ID 与版本号输出为十进制字符串，费率与金额字符串与 BigDecimal 精确互转
 * <li>组成类型、计算基础与舍入方式保持字符串传入，由 Processor 按 V1 目录解析并以参数错误拒绝非法值。</li>
 * <li>费率与金额输出为去掉末尾零的普通十进制字符串；本库无币种精度表，按币种精度补齐由 billing 计算时完成。</li>
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

    /** 转换作废请求。 */
    public CancelChannelCostVersionRequest toRequest(String planCode, String costVer,
        CancelChannelCostVersionRequestBody body) {
        CancelChannelCostVersionRequest request = new CancelChannelCostVersionRequest();
        request.setPlanCode(planCode);
        request.setCostVer(Integer.valueOf(costVer));
        request.setOperatorId(Long.valueOf(body.getOperatorId()));
        request.setReason(body.getReason());
        return request;
    }

    /** 可空的十进制主键查询参数；格式已由入口校验。 */
    public Long optionalId(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    /** 可空的方案状态查询参数；未知代码以参数错误拒绝。 */
    public ChannelCostPlanStatus planStatus(String status) {
        return status == null ? null : ChannelCostCodes.parse(ChannelCostPlanStatus.class, "status", status);
    }

    /** 转换方案列表项。 */
    public ChannelCostPlanSummaryResponseBody toSummaryResponseBody(ChannelCostPlanSummaryView view) {
        ChannelCostPlanSummaryResponseBody body = new ChannelCostPlanSummaryResponseBody();
        body.setPlanId(view.getPlanId().toString());
        body.setPlanCode(view.getPlanCode());
        body.setChannelId(view.getChannelId().toString());
        body.setChannelCode(view.getChannelCode());
        body.setChannelName(view.getChannelName());
        body.setStatus(view.getStatus().getCode());
        body.setCurrentCostVer(text(view.getCurrentCostVer()));
        body.setCurrentEffectiveFrom(view.getCurrentEffectiveFrom());
        body.setPendingApprovalCount(view.getPendingApprovalCount());
        body.setUpdateTime(view.getUpdateTime());
        return body;
    }

    /** 转换命中请求。 */
    public ResolveChannelCostRequest toRequest(ResolveChannelCostRequestBody body) {
        ResolveChannelCostRequest request = new ResolveChannelCostRequest();
        request.setChannelId(Long.valueOf(body.getChannelId()));
        request.setMidId(Long.valueOf(body.getMidId()));
        request.setEntityNo(body.getEntityNo());
        request.setCountryCode(body.getCountryCode());
        request.setCurrency(body.getCurrency());
        request.setPaymentMethodCode(body.getPaymentMethodCode());
        request.setActionType(body.getActionType());
        request.setAsOfTime(body.getAsOfTime());
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
        body.setCostVerId(version.getCostVerId().toString());
        body.setCostVer(version.getCostVer().toString());
        body.setScopeCount(version.getScopeCount());
        body.setComponentCount(version.getComponentCount());
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
        body.setCostVerId(view.getCostVerId().toString());
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

    /** 转换命中结果。 */
    public ChannelCostResolutionResponseBody toResponseBody(ChannelCostResolutionView view) {
        ChannelCostResolutionResponseBody body = new ChannelCostResolutionResponseBody();
        body.setPlanCode(view.getPlanCode());
        body.setCostVerId(view.getCostVerId().toString());
        body.setCostVer(view.getCostVer().toString());
        body.setContentHash(view.getContentHash());
        body.setEffectiveFrom(view.getEffectiveFrom());
        body.setEffectiveTo(view.getEffectiveTo());
        body.setScopeId(view.getScopeId().toString());
        body.setScopeNo(view.getScopeNo());
        body.setChannelAgreementRef(view.getChannelAgreementRef());
        body.setCurrency(view.getCurrency());
        body.setComponents(view.getComponents().stream().map(this::toComponentBody).toList());
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

    /** 转换成本组成；View 已去掉费率与金额的末尾零。 */
    ChannelCostComponentResponseBody toComponentBody(ChannelCostVersionView.Component component) {
        ChannelCostComponentResponseBody body = new ChannelCostComponentResponseBody();
        body.setComponentId(component.getComponentId().toString());
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
