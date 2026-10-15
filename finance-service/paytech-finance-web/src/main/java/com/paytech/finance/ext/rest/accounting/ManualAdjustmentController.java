package com.paytech.finance.ext.rest.accounting;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.finance.accounting.domain.view.ManualAdjustmentDetailView;
import com.paytech.finance.accounting.domainservice.service.ManualAdjustmentService;
import com.paytech.finance.accounting.service.processor.ApproveManualAdjustmentProcessor;
import com.paytech.finance.accounting.service.processor.RejectManualAdjustmentProcessor;
import com.paytech.finance.accounting.service.processor.SubmitManualAdjustmentProcessor;
import com.paytech.finance.api.accounting.ManualAdjustmentApi;
import com.paytech.finance.api.accounting.request.ReviewManualAdjustmentRequestBody;
import com.paytech.finance.api.accounting.request.SubmitManualAdjustmentRequestBody;
import com.paytech.finance.api.accounting.response.ManualAdjustmentDetailResponseBody;
import com.paytech.finance.api.accounting.response.ReviewManualAdjustmentResponseBody;
import com.paytech.finance.api.accounting.response.SubmitManualAdjustmentResponseBody;
import com.paytech.finance.ext.rest.accounting.converter.ManualAdjustmentConverter;
import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.AllArgsConstructor;

/** 人工会计调整的 HTTP 协议适配；详情查询直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class ManualAdjustmentController implements ManualAdjustmentApi {
    private final ManualAdjustmentService manualAdjustmentService;
    private final ManualAdjustmentConverter converter;
    private final RejectManualAdjustmentProcessor rejectManualAdjustmentProcessor;
    private final SubmitManualAdjustmentProcessor submitManualAdjustmentProcessor;
    private final ApproveManualAdjustmentProcessor approveManualAdjustmentProcessor;

    /** 提交调整并返回 201。 */
    @Override
    public ResponseEntity<SubmitManualAdjustmentResponseBody> submitManualAdjustment(
        SubmitManualAdjustmentRequestBody body) {
        SubmitManualAdjustmentResponseBody response =
            converter.toResponseBody(submitManualAdjustmentProcessor.process(converter.toRequest(body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 批准调整并过账。 */
    @Override
    public ReviewManualAdjustmentResponseBody approveManualAdjustment(String manualAdjustmentId,
        ReviewManualAdjustmentRequestBody body) {
        return converter.toResponseBody(
            approveManualAdjustmentProcessor.process(converter.toRequest(manualAdjustmentId, body)));
    }

    /** 驳回调整。 */
    @Override
    public ReviewManualAdjustmentResponseBody rejectManualAdjustment(String manualAdjustmentId,
        ReviewManualAdjustmentRequestBody body) {
        return converter.toResponseBody(
            rejectManualAdjustmentProcessor.process(converter.toRequest(manualAdjustmentId, body)));
    }

    /** 查询调整详情；不存在时业务拒绝。 */
    @Override
    public ManualAdjustmentDetailResponseBody getManualAdjustment(String manualAdjustmentId) {
        ManualAdjustmentDetailView view = manualAdjustmentService.findDetail(Long.parseLong(manualAdjustmentId));
        if (view == null) {
            throw new BizCheckFailException("人工调整不存在: " + manualAdjustmentId);
        }
        return converter.toResponseBody(view);
    }
}
