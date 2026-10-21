package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ReconciliationCashClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationCashModels.CashAccountResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationCashModels.CreateCashAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationCashModels.ReviewCashAccountRequestBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 对账银行账户的浏览器入口（块 123），全部简单转发 reconciliation CashAccountApi
 * <li>Gateway 绑定：查询 FINANCE_RECONCILIATION_READ；新建、申请关闭 FINANCE_RECONCILIATION_OPERATE；复核
 * FINANCE_RECONCILIATION_APPROVE。提交人不能复核由 reconciliation 判断。</li>
 * <li>新建请求含银行账号明文：/finance/reconciliation/v1/cash-accounts 在 web.logging 中只记元数据（I-05）。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/reconciliation/v1/cash-accounts")
public class ReconciliationCashController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final ReconciliationCashClient client;

    /** 银行账户分页（只含末四位）。 */
    @GetMapping
    public PagingResponseBody<CashAccountResponseBody> listCashAccounts(HttpServletRequest servletRequest,
        @RequestParam(value = "entityType", required = false) @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam(value = "entityId", required = false) @Pattern(regexp = "[A-Za-z0-9]{1,64}") String entityId,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "accountPurpose", required = false) @Pattern(regexp = "CHANNEL_SETTLEMENT|PAYOUT|TAX") String accountPurpose,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "PENDING_VERIFICATION|ACTIVE|SUSPENDED|CLOSED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("entityType", "entityId", "currency", "accountPurpose", "status", "page",
            "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listCashAccounts(entityType, entityId, currency, accountPurpose, status, paging.page(),
            paging.size());
    }

    /** 银行账户详情（只含末四位）。 */
    @GetMapping("/{cashAccountId}")
    public CashAccountResponseBody getCashAccount(HttpServletRequest servletRequest,
        @PathVariable("cashAccountId") @Pattern(regexp = ID) String cashAccountId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getCashAccount(cashAccountId);
    }

    /** 提交新建银行账户（待复核）。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CashAccountResponseBody createCashAccount(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid CreateCashAccountRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createCashAccount(context, body);
    }

    /** 申请关闭银行账户（待复核）。 */
    @PostMapping("/{cashAccountId}/closure")
    public CashAccountResponseBody requestCashAccountClosure(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("cashAccountId") @Pattern(regexp = ID) String cashAccountId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.requestCashAccountClosure(context, cashAccountId);
    }

    /** 复核银行账户新建或关闭申请。 */
    @PostMapping("/{cashAccountId}/approval")
    public CashAccountResponseBody reviewCashAccount(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("cashAccountId") @Pattern(regexp = ID) String cashAccountId,
        @RequestBody @NotNull @Valid ReviewCashAccountRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.reviewCashAccount(context, cashAccountId, body);
    }
}
