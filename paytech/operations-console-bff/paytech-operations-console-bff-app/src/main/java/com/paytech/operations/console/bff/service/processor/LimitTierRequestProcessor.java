package com.paytech.operations.console.bff.service.processor;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.domain.enums.LimitTier;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.RiskLimitTierClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.risklimittier.LabeledTierChangeBody;
import com.paytech.operations.console.bff.service.model.risklimittier.RequestTierChangePageRequestBody;
import com.paytech.operations.console.bff.service.model.risklimittier.TierChangeManualChecklistPageBody;
import com.paytech.operations.console.bff.service.processor.request.LimitTierChangeRequest;
import com.paytech.risk.decision.api.control.request.RequestClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.TierChangeChecklistBody;

import lombok.AllArgsConstructor;

/**
 * 提交档位变更申请（L4）：读取当前档位，上线复核（VERIFICATION → OBSERVATION）时重新读取清单系统项并附页面人工项，单次转发 Risk
 * <li>页面不能伪造系统项（严格解析拒绝未知属性）；上线复核任一系统项来源不可用返回 503，不调用 Risk 写接口。</li>
 * <li>其他迁移不附清单；页面人工项被忽略。迁移是否允许、清单是否满足与审批级别由 risk-decision 判定。</li>
 */
@Component
@AllArgsConstructor
public class LimitTierRequestProcessor extends AbstractProcessor<LimitTierChangeRequest, LabeledTierChangeBody> {
    private final LimitTierLabeler labeler;
    private final RiskLimitTierClient tiers;
    private final LimitTierChecklistAssembler checklist;

    @Override
    protected LabeledTierChangeBody processRequest(LimitTierChangeRequest request) {
        OperatorContext operator = request.getOperator();
        RequestTierChangePageRequestBody page = request.getBody();
        var detail = tiers.detail(operator, request.getClientId());
        boolean goLive = LimitTier.of(detail.getTier()) == LimitTier.VERIFICATION
            && LimitTier.of(page.getToTier()) == LimitTier.OBSERVATION;
        TierChangeChecklistBody checklistBody = goLive ? goLiveChecklist(operator, request.getClientId(), page.getChecklist()) : null;
        return labeler.label(tiers.request(operator, request.getClientId(), riskBody(page, checklistBody)));
    }

    /** 上线复核清单：系统项本次重新读取（任一不可用 503），人工项来自页面。 */
    TierChangeChecklistBody goLiveChecklist(OperatorContext operator, String clientId, TierChangeManualChecklistPageBody manual) {
        LimitTierChecklistReading reading;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LimitTierWorkspaceProcessor.DEADLINE_SECONDS);
            reading = checklist.read(executor, deadline, operator, clientId);
        }
        if (!reading.available()) {
            throw new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        TierChangeChecklistBody body = reading.systemChecklist();
        if (manual != null) {
            body.setChannelRegistrationConfirmed(manual.getChannelRegistrationConfirmed());
            body.setIntegrationConfirmed(manual.getIntegrationConfirmed());
            body.setProductionVerified(manual.getProductionVerified());
            body.setOpenIssuesCleared(manual.getOpenIssuesCleared());
        }
        return body;
    }

    /** 页面字段逐项转为 Risk 请求体。 */
    static RequestClientTierChangeRequestBody riskBody(RequestTierChangePageRequestBody page, TierChangeChecklistBody checklist) {
        RequestClientTierChangeRequestBody body = new RequestClientTierChangeRequestBody();
        body.setExpectedTierVersion(page.getExpectedTierVersion());
        body.setToTier(page.getToTier());
        body.setReason(page.getReason());
        body.setGoLiveNote(page.getGoLiveNote());
        body.setObservationTargetDays(page.getObservationTargetDays());
        body.setObservationTargetTransactions(page.getObservationTargetTransactions());
        body.setChecklist(checklist);
        return body;
    }
}
