package com.paytech.ledger.app.ext.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/** 账簿与统一账户维护的 HTTP 协议适配；命令成功后按操作人 Header 记一条 INFO 操作日志（不落库）。 */
@RestController
@AllArgsConstructor
public class LedgerBookController implements LedgerBookCommandApi {
    private static final Logger LOG = LoggerFactory.getLogger(LedgerBookController.class);

    private final LedgerSetupConverter converter;
    private final CreateAccountProcessor createAccountProcessor;
    private final CreateLedgerBookProcessor createLedgerBookProcessor;

    /** 创建账簿并返回 201。 */
    @Override
    public ResponseEntity<CreateLedgerBookResponseBody> createLedgerBook(String actorAccountId, String sessionId,
        CreateLedgerBookRequestBody body) {
        CreateLedgerBookResponseBody response = new CreateLedgerBookResponseBody();
        response.setLedgerBookId(createLedgerBookProcessor.process(converter.toRequest(body)).getId().toString());
        LOG.info("财务基础资料命令 action={} actor={} session={} resourceId={}", "CREATE_LEDGER_BOOK",
            actorAccountId, sessionId, response.getLedgerBookId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 创建账户并返回 201。 */
    @Override
    public ResponseEntity<CreateAccountResponseBody> createAccount(String actorAccountId, String sessionId,
        String ledgerBookId, CreateAccountRequestBody body) {
        CreateAccountResponseBody response = new CreateAccountResponseBody();
        response.setAccountId(createAccountProcessor.process(converter.toRequest(ledgerBookId, body)).getId().toString());
        LOG.info("财务基础资料命令 action={} actor={} session={} resourceId={}", "CREATE_ACCOUNT",
            actorAccountId, sessionId, response.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
