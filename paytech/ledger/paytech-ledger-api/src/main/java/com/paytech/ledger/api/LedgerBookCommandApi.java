package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.CreateAccountRequestBody;
import com.paytech.ledger.api.request.CreateLedgerBookRequestBody;
import com.paytech.ledger.api.response.CreateAccountResponseBody;
import com.paytech.ledger.api.response.CreateLedgerBookResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 维护账簿与统一账户（内部能力 Ledger & Account Balance）
 * <li>Finance 内部接口：命令操作人身份来自 Operations BFF 填入的 X-Gateway-Actor-Subject / X-Gateway-Session-Id Header，仅经 Operations BFF 转发，不得直接对外发布。</li>
 */
@HttpExchange(url = "/ledger-books/v1", accept = "application/json")
public interface LedgerBookCommandApi {

    /**
     * 创建 OPEN 账簿并初始化分录序号
     *
     * @param actorAccountId 操作人 Account ID，由 Operations BFF 从会话填入 X-Gateway-Actor-Subject；缺失时 400 INVALID_REQUEST
     * @param sessionId 操作人会话 ID，由 Operations BFF 填入 X-Gateway-Session-Id；缺失时 400 INVALID_REQUEST
     * @param body 请求
     * @return 201 与新账簿主键；同主体同币种已有账簿、币种或科目表未启用、财务主体无效时 400
     */
    @PostExchange
    ResponseEntity<CreateLedgerBookResponseBody> createLedgerBook(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateLedgerBookRequestBody body);

    /**
     * 在账簿下创建 ACTIVE 统一账户
     *
     * @param actorAccountId 操作人 Account ID，由 Operations BFF 从会话填入 X-Gateway-Actor-Subject；缺失时 400 INVALID_REQUEST
     * @param sessionId 操作人会话 ID，由 Operations BFF 填入 X-Gateway-Session-Id；缺失时 400 INVALID_REQUEST
     * @param ledgerBookId 账簿主键
     * @param body 请求
     * @return 201 与新账户主键；科目不可过账、违反账户身份矩阵或账户已存在时 400
     */
    @PostExchange("/{ledgerBookId}/accounts")
    ResponseEntity<CreateAccountResponseBody> createAccount(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @Valid @RequestBody CreateAccountRequestBody body);
}
