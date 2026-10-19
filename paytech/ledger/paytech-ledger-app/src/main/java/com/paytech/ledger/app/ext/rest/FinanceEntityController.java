package com.paytech.ledger.app.ext.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.FinanceEntityCommandApi;
import com.paytech.ledger.api.request.CreateFinanceEntityRequestBody;
import com.paytech.ledger.api.response.CreateFinanceEntityResponseBody;
import com.paytech.ledger.app.ext.rest.converter.LedgerSetupConverter;
import com.paytech.ledger.service.processor.CreateFinanceEntityProcessor;

import lombok.AllArgsConstructor;

/** 财务主体维护的 HTTP 协议适配；命令成功后按操作人 Header 记一条 INFO 操作日志（不落库）。 */
@RestController
@AllArgsConstructor
public class FinanceEntityController implements FinanceEntityCommandApi {
    private static final Logger LOG = LoggerFactory.getLogger(FinanceEntityController.class);

    private final LedgerSetupConverter converter;
    private final CreateFinanceEntityProcessor createFinanceEntityProcessor;

    /** 创建财务主体并返回 201。 */
    @Override
    public ResponseEntity<CreateFinanceEntityResponseBody> createFinanceEntity(String actorAccountId, String sessionId,
        CreateFinanceEntityRequestBody body) {
        CreateFinanceEntityResponseBody response = new CreateFinanceEntityResponseBody();
        response.setFinanceEntityId(createFinanceEntityProcessor.process(converter.toRequest(body)).getId().toString());
        LOG.info("财务基础资料命令 action={} actor={} session={} resourceId={}", "CREATE_FINANCE_ENTITY",
            actorAccountId, sessionId, response.getFinanceEntityId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
