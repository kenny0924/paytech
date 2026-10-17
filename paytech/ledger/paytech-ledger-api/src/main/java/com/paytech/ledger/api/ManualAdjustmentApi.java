package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.ReviewManualAdjustmentRequestBody;
import com.paytech.ledger.api.request.SubmitManualAdjustmentRequestBody;
import com.paytech.ledger.api.response.ManualAdjustmentDetailResponseBody;
import com.paytech.ledger.api.response.ReviewManualAdjustmentResponseBody;
import com.paytech.ledger.api.response.SubmitManualAdjustmentResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 人工会计调整 Maker-Checker（FIN-10 人工调整；内部能力 Accounting Instruction）
 * <li>提交即冻结内容摘要；复核人必须不同于编制人，并原样确认同一摘要。</li>
 * <li>批准后在同一本地事务内生成人工记账指令并过账；驳回后不可再审批。</li>
 * <li>Finance 内部开发接口：操作人标识由调用方传入，未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/manual-adjustments/v1", accept = "application/json")
public interface ManualAdjustmentApi {

    /**
     * 创建并提交人工调整
     *
     * @param body 请求
     * @return 201 与调整主键、单号和内容摘要；账簿/期间/账户/币种/精度/平衡不满足时 400
     */
    @PostExchange
    ResponseEntity<SubmitManualAdjustmentResponseBody> submitManualAdjustment(
        @Valid @RequestBody SubmitManualAdjustmentRequestBody body);

    /**
     * 批准人工调整并过账
     *
     * @param manualAdjustmentId 调整主键
     * @param body 复核请求
     * @return 200 与过账分录；同一复核人同一摘要重复批准返回原分录；其他已处理、同人复核、摘要不符或过账门禁失败时 400；同一调整并发复核时 409
     */
    @PostExchange("/{manualAdjustmentId}/approve")
    ReviewManualAdjustmentResponseBody approveManualAdjustment(
        @PathVariable("manualAdjustmentId") @Pattern(regexp = "[1-9][0-9]{0,18}") String manualAdjustmentId,
        @Valid @RequestBody ReviewManualAdjustmentRequestBody body);

    /**
     * 驳回人工调整
     *
     * @param manualAdjustmentId 调整主键
     * @param body 复核请求
     * @return 200 与 REJECTED 状态；同一复核人同一摘要重复驳回返回当前结果；其他已处理、同人复核或摘要不符时 400
     */
    @PostExchange("/{manualAdjustmentId}/reject")
    ReviewManualAdjustmentResponseBody rejectManualAdjustment(
        @PathVariable("manualAdjustmentId") @Pattern(regexp = "[1-9][0-9]{0,18}") String manualAdjustmentId,
        @Valid @RequestBody ReviewManualAdjustmentRequestBody body);

    /**
     * 查询人工调整详情
     *
     * @param manualAdjustmentId 调整主键
     * @return 调整头、明细、审批记录与过账结果；不存在时 400
     */
    @GetExchange("/{manualAdjustmentId}")
    ManualAdjustmentDetailResponseBody getManualAdjustment(
        @PathVariable("manualAdjustmentId") @Pattern(regexp = "[1-9][0-9]{0,18}") String manualAdjustmentId);
}
