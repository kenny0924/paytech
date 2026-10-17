package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.CreateFinanceEntityRequestBody;
import com.paytech.ledger.api.response.CreateFinanceEntityResponseBody;

import jakarta.validation.Valid;

/**
 * 维护 Finance 内部财务主体（内部能力 Finance Entity）
 * <li>Finance 内部开发接口：未接 Gateway 与统一身份，不得对外发布。</li>
 */
@HttpExchange(url = "/finance-entities/v1", accept = "application/json")
public interface FinanceEntityCommandApi {

    /**
     * 创建 ACTIVE 财务主体
     *
     * @param body 请求
     * @return 201 与新主体主键；代码或法律 Party 已存在时 400 BUSINESS_CHECK_FAILED
     */
    @PostExchange
    ResponseEntity<CreateFinanceEntityResponseBody> createFinanceEntity(
        @Valid @RequestBody CreateFinanceEntityRequestBody body);
}
