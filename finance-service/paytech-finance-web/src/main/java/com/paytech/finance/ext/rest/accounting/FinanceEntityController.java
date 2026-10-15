package com.paytech.finance.ext.rest.accounting;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.finance.accounting.service.processor.CreateFinanceEntityProcessor;
import com.paytech.finance.api.accounting.FinanceEntityCommandApi;
import com.paytech.finance.api.accounting.request.CreateFinanceEntityRequestBody;
import com.paytech.finance.api.accounting.response.CreateFinanceEntityResponseBody;
import com.paytech.finance.ext.rest.accounting.converter.LedgerSetupConverter;

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
