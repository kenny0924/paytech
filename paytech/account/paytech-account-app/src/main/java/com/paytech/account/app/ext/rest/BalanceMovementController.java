package com.paytech.account.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.BalanceMovementApi;
import com.paytech.account.api.request.BalanceMovementRequestBody;
import com.paytech.account.api.response.BalanceMovementResponseBody;
import com.paytech.account.app.ext.rest.converter.BalanceMovementConverter;
import com.paytech.account.service.processor.MoveBalanceProcessor;

import lombok.AllArgsConstructor;

/** 余额桶移动命令的 HTTP 协议适配：首次执行 201，同来源重放 200。 */
@RestController
@AllArgsConstructor
public class BalanceMovementController implements BalanceMovementApi {
    private final BalanceMovementConverter converter;
    private final MoveBalanceProcessor processor;

    /** 执行或重放一次桶移动。 */
    @Override
    public ResponseEntity<BalanceMovementResponseBody> moveBalance(BalanceMovementRequestBody body) {
        BalanceMovementResponseBody response = converter.toResponseBody(processor.process(converter.toRequest(body)));
        return ResponseEntity.status(response.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
