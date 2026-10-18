package com.paytech.ledger.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.FinanceEntityCommandApi;
import com.paytech.ledger.api.request.CreateFinanceEntityRequestBody;
import com.paytech.ledger.api.response.CreateFinanceEntityResponseBody;
import com.paytech.ledger.app.ext.rest.converter.LedgerSetupConverter;
import com.paytech.ledger.service.processor.CreateFinanceEntityProcessor;

import lombok.AllArgsConstructor;

/** 财务主体维护的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class FinanceEntityController implements FinanceEntityCommandApi {
    private final LedgerSetupConverter converter;
    private final CreateFinanceEntityProcessor createFinanceEntityProcessor;

    /** 创建财务主体并返回 201。 */
    @Override
    public ResponseEntity<CreateFinanceEntityResponseBody> createFinanceEntity(CreateFinanceEntityRequestBody body) {
        CreateFinanceEntityResponseBody response = new CreateFinanceEntityResponseBody();
        response.setFinanceEntityId(createFinanceEntityProcessor.process(converter.toRequest(body)).getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
