package com.paytech.finance.api.accounting;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.finance.api.accounting.request.CreateAccountRequestBody;
import com.paytech.finance.api.accounting.request.CreateLedgerBookRequestBody;
import com.paytech.finance.api.accounting.response.CreateAccountResponseBody;
import com.paytech.finance.api.accounting.response.CreateLedgerBookResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 维护账簿与统一账户（内部能力 Ledger & Account Balance）
 * <li>Finance 内部开发接口：未接 Gateway 与统一身份，不得对外发布。</li>
 */
@HttpExchange(url = "/ledger-books/v1", accept = "application/json")
public interface LedgerBookCommandApi {

    /**
     * 创建 OPEN 账簿并初始化分录序号
     *
     * @param body 请求
     * @return 201 与新账簿主键；同主体同币种已有账簿、币种或科目表未启用、财务主体无效时 400
     */
    @PostExchange
    ResponseEntity<CreateLedgerBookResponseBody> createLedgerBook(@Valid @RequestBody CreateLedgerBookRequestBody body);

    /**
     * 在账簿下创建 ACTIVE 统一账户
     *
     * @param ledgerBookId 账簿主键
     * @param body 请求
     * @return 201 与新账户主键；科目不可过账、违反账户身份矩阵或账户已存在时 400
     */
    @PostExchange("/{ledgerBookId}/accounts")
    ResponseEntity<CreateAccountResponseBody> createAccount(
        @PathVariable("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @Valid @RequestBody CreateAccountRequestBody body);
}
