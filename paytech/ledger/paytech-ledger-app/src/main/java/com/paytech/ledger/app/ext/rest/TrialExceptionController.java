package com.paytech.ledger.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.TrialExceptionApi;
import com.paytech.ledger.api.request.ApproveProjectionRebuildRequestBody;
import com.paytech.ledger.api.request.StartProjectionRebuildRequestBody;
import com.paytech.ledger.api.response.TrialExceptionResponseBody;
import com.paytech.ledger.app.ext.rest.converter.TrialExceptionConverter;
import com.paytech.ledger.service.processor.ApproveProjectionRebuildProcessor;
import com.paytech.ledger.service.processor.StartProjectionRebuildProcessor;

import lombok.AllArgsConstructor;

/** 试算异常处置的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class TrialExceptionController implements TrialExceptionApi {
    private final TrialExceptionConverter converter;
    private final StartProjectionRebuildProcessor startProjectionRebuildProcessor;
    private final ApproveProjectionRebuildProcessor approveProjectionRebuildProcessor;

    /** 发起投影重建。 */
    @Override
    public TrialExceptionResponseBody startProjectionRebuild(String exceptionId, StartProjectionRebuildRequestBody body) {
        return converter.toResponseBody(startProjectionRebuildProcessor.process(converter.toRequest(exceptionId, body)));
    }

    /** 批准投影重建。 */
    @Override
    public TrialExceptionResponseBody approveProjectionRebuild(String exceptionId,
        ApproveProjectionRebuildRequestBody body) {
        return converter.toResponseBody(
            approveProjectionRebuildProcessor.process(converter.toRequest(exceptionId, body)));
    }
}
