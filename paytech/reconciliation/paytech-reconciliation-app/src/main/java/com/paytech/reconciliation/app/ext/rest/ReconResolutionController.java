package com.paytech.reconciliation.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;
import com.paytech.reconciliation.api.exception.ReconResolutionApi;
import com.paytech.reconciliation.api.exception.request.ReviewReconResolutionRequestBody;
import com.paytech.reconciliation.api.exception.response.ReconResolutionResponseBody;
import com.paytech.reconciliation.app.ext.rest.converter.ReconExceptionConverter;
import com.paytech.reconciliation.service.processor.ReviewReconResolutionProcessor;
import com.paytech.reconciliation.service.processor.response.ReconResolutionResponse;
import lombok.AllArgsConstructor;

/** 异常处置复核的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class ReconResolutionController implements ReconResolutionApi {
    private final ReconExceptionConverter converter;
    private final ReviewReconResolutionProcessor reviewReconResolutionProcessor;

    /** 复核（批准即应用）。 */
    @Override
    public ReconResolutionResponseBody reviewReconResolution(String actorAccountId, String sessionId,
        String resolutionId, ReviewReconResolutionRequestBody body) {
        ReconResolutionResponse response = reviewReconResolutionProcessor.process(
            converter.toReviewRequest(actorAccountId, resolutionId, body));
        return converter.toResolutionBody(response.getResolution(), response.getException().getCurrency());
    }
}
