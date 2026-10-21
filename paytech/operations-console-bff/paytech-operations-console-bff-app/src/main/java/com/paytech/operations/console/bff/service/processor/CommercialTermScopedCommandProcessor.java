package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.integration.CommercialPricingClient;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionResponseBody;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermScopedCommandRequest;
import com.paytech.operations.console.bff.service.processor.request.CommercialTermScopedCommandRequest.Scope;

import lombok.AllArgsConstructor;

/**
 * 条款版本写入的路由范围隔离：先读取方案 / 版本的 Owner 类型，与路由范围一致才执行那一次 commercial 写入
 * <li>标准价目模板的写与复核走独立路由与权限（COMMERCIAL_TEMPLATE_*），合同价目走 COMMERCIAL_PRICING_*；同一 commercial 接口按
 * owner_type 区分校验，BFF 在此保证两类权限不能经对方路由写入。</li>
 * <li>版本级命令读取版本概要的 ownerType；方案级命令（建版本）按模板方案列表判定 planId 是否为模板（commercial 不提供按 ID 读方案）。</li>
 * <li>范围不符为 400 BUSINESS_CHECK_FAILED，commercial 不收到写入；读取失败按原错误返回。只做前置读取与转发，不编排跨系统事务。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialTermScopedCommandProcessor
    extends AbstractProcessor<CommercialTermScopedCommandRequest, CommercialTermVersionResponseBody> {
    private static final String TEMPLATE = CommercialTermOwnerType.PLATFORM_TEMPLATE.getCode();

    private final CommercialPricingClient pricing;
    private final CommercialConsoleQueryClient queries;

    @Override
    protected CommercialTermVersionResponseBody processRequest(CommercialTermScopedCommandRequest request) {
        if (request.getPlanId() != null) {
            requireScope(request.getScope(), isTemplatePlan(request.getPlanId()), "方案 " + request.getPlanId());
        } else {
            requireScope(request.getScope(), isTemplateVersion(request.getVersionId()), "版本 " + request.getVersionId());
        }
        return request.getCommand().get();
    }

    /** 模板方案列表中是否有该方案。 */
    boolean isTemplatePlan(String planId) {
        return queries.listTemplates(null, null, null).getTemplates().stream()
            .anyMatch(template -> planId.equals(template.getPlanId()));
    }

    /** 版本概要的 Owner 类型是否为模板；缺失视为上游无效响应。 */
    boolean isTemplateVersion(String versionId) {
        String ownerType = pricing.version(versionId).getOwnerType();
        if (ownerType == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return TEMPLATE.equals(ownerType);
    }

    /** 路由范围与 Owner 类型不符时拒绝。 */
    static void requireScope(Scope scope, boolean template, String target) {
        if (scope == Scope.TEMPLATE && !template) {
            throw new BizCheckFailException("不是标准价目模板，请在价目页维护: " + target);
        }
        if (scope == Scope.CONTRACT && template) {
            throw new BizCheckFailException("标准价目模板请在标准价目页维护: " + target);
        }
    }
}
