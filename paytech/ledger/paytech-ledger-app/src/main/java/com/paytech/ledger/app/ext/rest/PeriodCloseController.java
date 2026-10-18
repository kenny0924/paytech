package com.paytech.ledger.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.PeriodCloseApi;
import com.paytech.ledger.api.request.ReviewPeriodCloseRequestBody;
import com.paytech.ledger.api.request.StartPeriodCloseRequestBody;
import com.paytech.ledger.api.request.StartPeriodReopenRequestBody;
import com.paytech.ledger.api.response.PeriodCloseDetailResponseBody;
import com.paytech.ledger.api.response.PeriodCloseResponseBody;
import com.paytech.ledger.app.ext.rest.converter.PeriodCloseConverter;
import com.paytech.ledger.domainservice.service.PeriodCloseService;
import com.paytech.ledger.service.processor.ApprovePeriodCloseProcessor;
import com.paytech.ledger.service.processor.RejectPeriodCloseProcessor;
import com.paytech.ledger.service.processor.StartPeriodCloseProcessor;
import com.paytech.ledger.service.processor.StartPeriodReopenProcessor;

import lombok.AllArgsConstructor;

/** 期间关账与重开的 HTTP 协议适配；查询直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class PeriodCloseController implements PeriodCloseApi {
    private final PeriodCloseService periodCloseService;
    private final PeriodCloseConverter converter;
    private final StartPeriodCloseProcessor startPeriodCloseProcessor;
    private final StartPeriodReopenProcessor startPeriodReopenProcessor;
    private final RejectPeriodCloseProcessor rejectPeriodCloseProcessor;
    private final ApprovePeriodCloseProcessor approvePeriodCloseProcessor;

    /** 发起关账并返回 201。 */
    @Override
    public ResponseEntity<PeriodCloseResponseBody> startPeriodClose(String periodId, StartPeriodCloseRequestBody body) {
        PeriodCloseResponseBody response =
            converter.toResponseBody(startPeriodCloseProcessor.process(converter.toRequest(periodId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 发起重开并返回 201。 */
    @Override
    public ResponseEntity<PeriodCloseResponseBody> startPeriodReopen(String periodId, StartPeriodReopenRequestBody body) {
        PeriodCloseResponseBody response =
            converter.toResponseBody(startPeriodReopenProcessor.process(converter.toRequest(periodId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 批准关账或重开。 */
    @Override
    public PeriodCloseResponseBody approvePeriodClose(String periodId, String closeNo, ReviewPeriodCloseRequestBody body) {
        return converter.toResponseBody(approvePeriodCloseProcessor.process(converter.toRequest(periodId, closeNo, body)));
    }

    /** 驳回关账或重开。 */
    @Override
    public PeriodCloseResponseBody rejectPeriodClose(String periodId, String closeNo, ReviewPeriodCloseRequestBody body) {
        return converter.toResponseBody(rejectPeriodCloseProcessor.process(converter.toRequest(periodId, closeNo, body)));
    }

    /** 分页查询期间的关账流程。 */
    @Override
    public PagingResponseBody<PeriodCloseResponseBody> listPeriodCloses(String periodId, Integer page, Integer size) {
        return periodCloseService.pageCloses(Long.parseLong(periodId), PagingRequestBody.of(page, size))
            .map(converter::toResponseBody);
    }

    /** 查询关账流程详情。 */
    @Override
    public PeriodCloseDetailResponseBody getPeriodClose(String periodId, String closeNo) {
        return converter.toResponseBody(periodCloseService.getClose(Long.parseLong(periodId), Integer.parseInt(closeNo)));
    }
}
