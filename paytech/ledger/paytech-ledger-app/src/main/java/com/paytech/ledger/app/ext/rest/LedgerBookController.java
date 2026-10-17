package com.paytech.ledger.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.LedgerBookCommandApi;
import com.paytech.ledger.api.request.CreateAccountRequestBody;
import com.paytech.ledger.api.request.CreateLedgerBookRequestBody;
import com.paytech.ledger.api.response.CreateAccountResponseBody;
import com.paytech.ledger.api.response.CreateLedgerBookResponseBody;
import com.paytech.ledger.app.ext.rest.converter.LedgerSetupConverter;
import com.paytech.ledger.service.processor.CreateAccountProcessor;
import com.paytech.ledger.service.processor.CreateLedgerBookProcessor;

import lombok.AllArgsConstructor;

/** 账簿与统一账户维护的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class LedgerBookController implements LedgerBookCommandApi {
    private final LedgerSetupConverter converter;
    private final CreateAccountProcessor createAccountProcessor;
    private final CreateLedgerBookProcessor createLedgerBookProcessor;

    /** 创建账簿并返回 201。 */
    @Override
    public ResponseEntity<CreateLedgerBookResponseBody> createLedgerBook(CreateLedgerBookRequestBody body) {
        CreateLedgerBookResponseBody response = new CreateLedgerBookResponseBody();
        response.setLedgerBookId(createLedgerBookProcessor.process(converter.toRequest(body)).getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 创建账户并返回 201。 */
    @Override
    public ResponseEntity<CreateAccountResponseBody> createAccount(String ledgerBookId, CreateAccountRequestBody body) {
        CreateAccountResponseBody response = new CreateAccountResponseBody();
        response.setAccountId(createAccountProcessor.process(converter.toRequest(ledgerBookId, body)).getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
