package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.domain.enums.UnderwritingInputStatus;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.integration.RiskUnderwritingClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingInputAssembly;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingWorkspaceResponseBody;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingWorkspaceRequest;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.risk.decision.api.control.request.UnderwritingPreviewRequestBody;
import com.paytech.risk.decision.api.control.response.ClientUnderwritingCurrentResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingPreviewResponseBody;

import lombok.AllArgsConstructor;

/**
 * 承保工作台（技术方案 10.1）：组装输入快照、读取当前承保，有维护权限时调用 Risk 预览自动承保判定与估算敞口
 * <li>client-master 主档与 risk-decision 失败整页失败；compliance、申请资料、分类与 MCC 失败只把对应来源标为 UNAVAILABLE。</li>
 * <li>维护权限按承保预览路由向 Platform 实时授权判断（O-5）：无权限不调用预览、不能提交。</li>
 * <li>预览的结算周期建议取进行中草稿的值，没有进行中版本时为空（按 0 计）。</li>
 */
@Component
@AllArgsConstructor
public class UnderwritingWorkspaceProcessor extends AbstractProcessor<UnderwritingWorkspaceRequest, UnderwritingWorkspaceResponseBody> {
    /** 承保预览路由；持有该路由权限（RISK_UNDERWRITING_MANAGE）即视为可维护。 */
    static final String PREVIEW_ROUTE = "operations-risk-underwriting-preview";

    private final UnderwritingLabeler labeler;
    private final RiskUnderwritingClient underwritings;
    private final PlatformOperationsClient platform;
    private final UnderwritingInputAssembler assembler;

    @Override
    protected UnderwritingWorkspaceResponseBody processRequest(UnderwritingWorkspaceRequest request) {
        OperatorContext operator = request.getOperator();
        UnderwritingInputAssembly assembly = assembler.assemble(operator, request.getClientId(), false);
        ClientUnderwritingCurrentResponseBody current = underwritings.current(operator, request.getClientId());
        boolean canManage = canManage(operator);
        UnderwritingPreviewResponseBody preview = canManage ? underwritings.preview(operator, previewBody(assembly, current)) : null;
        boolean canSubmit = canManage && assembly.getStatus() == UnderwritingInputStatus.READY;
        return new UnderwritingWorkspaceResponseBody(assembly.getClient(), assembly.getInput(), assembly.getStatus().getCode(),
            assembly.getSources(), preview, labeler.labelCurrent(current), canManage, canSubmit);
    }

    /** 按预览路由实时授权；只有权限拒绝视为无维护权限，其他失败原样传播。 */
    boolean canManage(OperatorContext operator) {
        try {
            platform.authorize(new OperatorContext(operator.accountId(), operator.sessionId(), PREVIEW_ROUTE));
            return true;
        } catch (FailException failure) {
            if (OperationsErrorCode.OPERATIONS_PERMISSION_DENIED.getCode().equals(failure.getCode())) {
                return false;
            }
            throw failure;
        }
    }

    /** 预览输入与进行中草稿的结算周期建议。 */
    static UnderwritingPreviewRequestBody previewBody(UnderwritingInputAssembly assembly, ClientUnderwritingCurrentResponseBody current) {
        UnderwritingPreviewRequestBody body = new UnderwritingPreviewRequestBody();
        body.setInput(assembly.getInput());
        var open = current.getOpen();
        body.setSuggestedSettlementDays(open == null || open.getDecision() == null ? null
            : open.getDecision().getSuggestedSettlementDays());
        return body;
    }
}
