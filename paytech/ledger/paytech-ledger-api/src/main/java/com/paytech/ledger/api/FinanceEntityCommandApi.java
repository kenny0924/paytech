package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.CreateFinanceEntityRequestBody;
import com.paytech.ledger.api.response.CreateFinanceEntityResponseBody;

import jakarta.validation.Valid;

/**
 * 维护 Finance 内部财务主体（内部能力 Finance Entity）
 * <li>Finance 内部接口：命令操作人身份来自 Operations BFF 填入的 X-Gateway-Actor-Subject / X-Gateway-Session-Id Header，仅经 Operations BFF 转发，不得直接对外发布。</li>
 */
@HttpExchange(url = "/finance-entities/v1", accept = "application/json")
public interface FinanceEntityCommandApi {

    /**
     * 创建 ACTIVE 财务主体
     *
     * @param actorAccountId 操作人 Account ID，由 Operations BFF 从会话填入 X-Gateway-Actor-Subject；缺失时 400 INVALID_REQUEST
     * @param sessionId 操作人会话 ID，由 Operations BFF 填入 X-Gateway-Session-Id；缺失时 400 INVALID_REQUEST
     * @param body 请求
     * @return 201 与新主体主键；代码或法律 Party 已存在时 400 FINANCE_ENTITY_ALREADY_EXISTS
     */
    @PostExchange
    ResponseEntity<CreateFinanceEntityResponseBody> createFinanceEntity(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateFinanceEntityRequestBody body);
}
