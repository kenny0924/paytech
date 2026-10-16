package com.paytech.finance.ext.rest.accounting;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.finance.accounting.service.processor.ApproveProjectionRebuildProcessor;
import com.paytech.finance.accounting.service.processor.StartProjectionRebuildProcessor;
import com.paytech.finance.api.accounting.TrialExceptionApi;
import com.paytech.finance.api.accounting.request.ApproveProjectionRebuildRequestBody;
import com.paytech.finance.api.accounting.request.StartProjectionRebuildRequestBody;
import com.paytech.finance.api.accounting.response.TrialExceptionResponseBody;
import com.paytech.finance.ext.rest.accounting.converter.TrialExceptionConverter;

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
