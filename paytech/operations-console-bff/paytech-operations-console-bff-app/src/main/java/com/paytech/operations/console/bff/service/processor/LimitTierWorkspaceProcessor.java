package com.paytech.operations.console.bff.service.processor;

import static com.paytech.operations.console.bff.service.processor.ClientReviewDetailsProcessor.withRequestContext;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.domain.enums.LimitTier;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.integration.RiskLimitTierClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierClientBody;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierWorkspaceResponseBody;
import com.paytech.operations.console.bff.service.processor.request.LimitTierClientRequest;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.risk.decision.api.control.response.ClientTierDetailResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;

import lombok.AllArgsConstructor;

/**
 * Client 档位工作台（技术方案 8.4）：并发读取档位详情（L2）、变更历史（L3）与上线复核清单三个来源，总时限 3 秒
 * <li>risk-decision 档位与历史失败或超时整页失败；承保、资格与合同来源失败只把对应系统项标为 UNAVAILABLE。</li>
 * <li>申请权限按申请路由向 Platform 实时授权判断；可申请目标按当前档位（B3）计算，已有待审批申请时为空。</li>
 */
@Component
@AllArgsConstructor
public class LimitTierWorkspaceProcessor extends AbstractProcessor<LimitTierClientRequest, LimitTierWorkspaceResponseBody> {
    /** 工作台读取总时限（秒）。 */
    static final long DEADLINE_SECONDS = 3;

    /** 档位申请路由；持有该路由权限（RISK_LIMIT_TIER_REQUEST）即可申请。 */
    static final String REQUEST_ROUTE = "operations-risk-limit-tier-request";

    private final LimitTierLabeler labeler;
    private final RiskLimitTierClient tiers;
    private final PlatformOperationsClient platform;
    private final LimitTierChecklistAssembler checklist;

    @Override
    protected LimitTierWorkspaceResponseBody processRequest(LimitTierClientRequest request) {
        OperatorContext operator = request.getOperator();
        String clientId = request.getClientId();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
            var detailFuture = executor.submit(withRequestContext(() -> tiers.detail(operator, clientId)));
            var historyFuture = executor.submit(withRequestContext(() -> tiers.history(operator, clientId)));
            LimitTierChecklistReading reading = checklist.read(executor, deadline, operator, clientId);
            ClientTierDetailResponseBody detail = tierOrNull(LimitTierChecklistAssembler.required(detailFuture, deadline));
            var history = labeler.labelWorkspace(detail, LimitTierChecklistAssembler.required(historyFuture, deadline));
            boolean canRequest = canRequest(operator);
            return new LimitTierWorkspaceResponseBody(client(clientId, detail, reading.effective(), reading), detail, history,
                reading.underwriting(), reading.checklist(), canRequest, requestableTargets(detail, canRequest));
        }
    }

    /** 无档位（tier 为 null）的详情按 null 返回页面。 */
    static ClientTierDetailResponseBody tierOrNull(ClientTierDetailResponseBody detail) {
        return detail.getTier() == null ? null : detail;
    }

    /** 按申请路由实时授权；只有权限拒绝视为无申请权限，其他失败原样传播。 */
    boolean canRequest(OperatorContext operator) {
        try {
            platform.authorize(new OperatorContext(operator.accountId(), operator.sessionId(), REQUEST_ROUTE));
            return true;
        } catch (FailException failure) {
            if (OperationsErrorCode.OPERATIONS_PERMISSION_DENIED.getCode().equals(failure.getCode())) {
                return false;
            }
            throw failure;
        }
    }

    /** 可申请的目标档位：无档位、未知档位、已有待审批或无申请权限时为空。 */
    static List<String> requestableTargets(ClientTierDetailResponseBody detail, boolean canRequest) {
        LimitTier tier = detail == null ? null : LimitTier.of(detail.getTier());
        if (!canRequest || tier == null || detail.getPendingChange() != null) {
            return List.of();
        }
        return tier.requestableTargets(LimitTier.of(detail.getPreviousTier()));
    }

    /** Client 摘要：有档位取建档快照，否则取有效（或最新）承保快照，都没有时只有 clientId。 */
    static LimitTierClientBody client(String clientId, ClientTierDetailResponseBody detail, UnderwritingResponseBody effective,
        LimitTierChecklistReading reading) {
        if (detail != null) {
            return new LimitTierClientBody(clientId, detail.getClientNo(), detail.getClientDisplayName(), detail.getClientType(),
                detail.getJurisdictionCode());
        }
        UnderwritingResponseBody snapshot = effective != null ? effective
            : reading.getCurrent() == null ? null : reading.getCurrent().getLatest();
        if (snapshot == null || snapshot.getInput() == null) {
            return new LimitTierClientBody(clientId, null, null, null, null);
        }
        var input = snapshot.getInput();
        return new LimitTierClientBody(clientId, input.getClientNo(), input.getClientDisplayName(), input.getClientType(),
            input.getJurisdictionCode());
    }
}
