package com.paytech.ledger.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.ApproveProjectionRebuildRequestBody;
import com.paytech.ledger.api.request.StartProjectionRebuildRequestBody;
import com.paytech.ledger.api.response.TrialExceptionResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 试算异常处置（内部能力 Trial Balance &amp; Period Close）
 * <li>本期只支持 GENERAL_LEDGER_MISMATCH/ACCOUNT 异常的投影重建：从 Journal 重算账户投影，不写 Journal、不改变序号。</li>
 * <li>Maker 发起、不同的 Checker 批准；Finance 内部开发接口，未接 Gateway 与统一身份，不得对外发布。</li>
 */
@HttpExchange(url = "/trial-balance-exceptions/v1", accept = "application/json")
public interface TrialExceptionApi {

    /**
     * 发起投影重建
     *
     * @param exceptionId 试算异常主键
     * @param body 请求
     * @return 200 与 UNDER_REVIEW 异常；异常不存在、类型不支持或状态不是 OPEN 时 400
     */
    @PostExchange("/{exceptionId}/projection-rebuild")
    TrialExceptionResponseBody startProjectionRebuild(
        @PathVariable("exceptionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String exceptionId,
        @Valid @RequestBody StartProjectionRebuildRequestBody body);

    /**
     * 批准投影重建并执行
     *
     * @param exceptionId 试算异常主键
     * @param body 请求
     * @return 200 与 RESOLVED 异常（含解决摘要）；状态不是 UNDER_REVIEW 或复核人与编制人相同时 400；同一异常并发批准时 409
     */
    @PostExchange("/{exceptionId}/projection-rebuild/approve")
    TrialExceptionResponseBody approveProjectionRebuild(
        @PathVariable("exceptionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String exceptionId,
        @Valid @RequestBody ApproveProjectionRebuildRequestBody body);
}
