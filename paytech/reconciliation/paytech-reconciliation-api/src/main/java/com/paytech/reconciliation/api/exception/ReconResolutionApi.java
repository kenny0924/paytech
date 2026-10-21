package com.paytech.reconciliation.api.exception;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.reconciliation.api.ReconciliationHeaders;
import com.paytech.reconciliation.api.exception.request.ReviewReconResolutionRequestBody;
import com.paytech.reconciliation.api.exception.response.ReconResolutionResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 异常处置复核（Checker，UC-R16）
 * <li>复核人必须不同于提交人，并原样确认提交时的 contentHash。批准在同一本地事务内应用处置：处置 APPLIED、异常 RESOLVED，并按来源对象重算
 * 所属账单（对平检查）或预期银行流水状态；平台流水、预期银行流水、银行流水的 CLASSIFY 在同一事务登记 ReconAdjustmentApproved 发布，
 * 渠道账单行的归类随所属账单的 ChannelBatchReconciled 带出。</li>
 * <li>拒绝：处置 REJECTED，异常回到 OPEN。</li>
 * <li>操作人取自 {@link ReconciliationHeaders} 身份头。</li>
 */
@HttpExchange(url = "/reconciliation/v1/recon-resolutions", accept = "application/json")
public interface ReconResolutionApi {

    /**
     * 复核处置
     *
     * @param actorAccountId 复核人 Account ID，必须不同于提交人；缺失或格式错误时 400
     * @param sessionId 复核人会话 ID；缺失时 400
     * @param resolutionId 处置主键
     * @param body 复核决定与确认的内容摘要
     * @return 200 与复核后的处置；同一复核人同一决定重复提交返回当前结果；处置不是 PENDING_CHECK、同人复核、摘要不符，或批准时应用条件已
     *         不满足（如目标剩余金额不足）时 400；并发时 409
     */
    @PostExchange("/{resolutionId}/approval")
    ReconResolutionResponseBody reviewReconResolution(
        @RequestHeader(ReconciliationHeaders.ACTOR_HEADER) @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader(ReconciliationHeaders.SESSION_HEADER) String sessionId,
        @PathVariable("resolutionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String resolutionId,
        @Valid @RequestBody ReviewReconResolutionRequestBody body);
}
