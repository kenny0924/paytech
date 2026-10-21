package com.paytech.reconciliation.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.api.exception.ReconExceptionApi;
import com.paytech.reconciliation.api.exception.request.OpenReconExceptionRequestBody;
import com.paytech.reconciliation.api.exception.request.SubmitReconResolutionRequestBody;
import com.paytech.reconciliation.api.exception.response.ReconExceptionDetailResponseBody;
import com.paytech.reconciliation.api.exception.response.ReconExceptionResponseBody;
import com.paytech.reconciliation.api.exception.response.ReconResolutionResponseBody;
import com.paytech.reconciliation.app.ext.rest.converter.ReconExceptionConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconciliationWire;
import com.paytech.reconciliation.domainservice.service.ReconExceptionQueryService;
import com.paytech.reconciliation.service.processor.OpenReconExceptionProcessor;
import com.paytech.reconciliation.service.processor.SubmitReconResolutionProcessor;
import com.paytech.reconciliation.service.processor.response.ReconResolutionResponse;
import lombok.AllArgsConstructor;

/** 对账异常人工开立、查询与提交处置的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class ReconExceptionController implements ReconExceptionApi {
    private final ReconExceptionConverter converter;
    private final ReconExceptionQueryService reconExceptionQueryService;
    private final OpenReconExceptionProcessor openReconExceptionProcessor;
    private final SubmitReconResolutionProcessor submitReconResolutionProcessor;

    /** 人工开立并返回 201。 */
    @Override
    public ResponseEntity<ReconExceptionResponseBody> openReconException(String actorAccountId, String sessionId,
        OpenReconExceptionRequestBody body) {
        ReconExceptionResponseBody response = converter.toExceptionBody(
            openReconExceptionProcessor.process(converter.toOpenRequest(actorAccountId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 分页查询（账龄从长到短；默认只查未结）。 */
    @Override
    public PagingResponseBody<ReconExceptionResponseBody> listReconExceptions(String status, String exceptionType,
        String reconLayer, String clearingAccountId, String cashAccountId, String statementId, Integer page,
        Integer size) {
        return reconExceptionQueryService.listExceptions(ReconciliationWire.parse(ReconExceptionStatus.class, status),
            ReconciliationWire.parse(ReconExceptionType.class, exceptionType),
            ReconciliationWire.parse(ReconLayer.class, reconLayer), ReconciliationWire.id(clearingAccountId),
            ReconciliationWire.id(cashAccountId), ReconciliationWire.id(statementId), PagingRequestBody.of(page, size))
            .map(converter::toExceptionBody);
    }

    /** 详情。 */
    @Override
    public ReconExceptionDetailResponseBody getReconException(String exceptionId) {
        return converter.toDetailBody(reconExceptionQueryService.getException(Long.parseLong(exceptionId)));
    }

    /** 提交处置并返回 201。 */
    @Override
    public ResponseEntity<ReconResolutionResponseBody> submitReconResolution(String actorAccountId, String sessionId,
        String exceptionId, SubmitReconResolutionRequestBody body) {
        ReconResolutionResponse response = submitReconResolutionProcessor.process(
            converter.toSubmitRequest(actorAccountId, exceptionId, body));
        return ResponseEntity.status(HttpStatus.CREATED).body(converter.toResolutionBody(response.getResolution(),
            response.getException().getCurrency()));
    }
}
