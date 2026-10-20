package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;
import static com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.ENTITY_NO_PATTERN;

import java.time.Instant;
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
import com.paytech.operations.console.bff.integration.FinanceBillingFactClient;
import com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.BillingFactResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.BillingFactSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.SubmitBillingFactRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 计费事实人工登记的浏览器入口（块 100），简单转发 billing；浏览器路径 /finance/billing-facts/v1 对应 billing /billing/v1/facts
 * <li>Gateway 绑定：列表、详情 FINANCE_ACCOUNTING_READ；登记、作废 FINANCE_ACCOUNTING_MANAGE；批准 FINANCE_ACCOUNTING_APPROVE，并经 PLT-I02 实时授权。</li>
 * <li>列表转发 billing 管理检索 /search；登记时 BFF 固定来源系统并生成来源引用；批准与作废不接受 Body。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/billing-facts/v1")
public class FinanceBillingFactController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final FinanceBillingFactClient client;

    /** 按主体与币种分页检索计费事实（含草稿与已作废），按事实 ID 倒序。 */
    @GetMapping
    public PagingResponseBody<BillingFactSummaryResponseBody> searchBillingFacts(HttpServletRequest servletRequest,
        @RequestParam("entityNo") @Pattern(regexp = ENTITY_NO_PATTERN) String entityNo,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "clientId", required = false) @Pattern(regexp = ID) String clientId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|APPROVED|SUPERSEDED|VOIDED") String status,
        @RequestParam(value = "factType", required = false) @Pattern(regexp = "[A-Z][A-Z_]{0,63}") String factType,
        @RequestParam(value = "createdFrom", required = false) Instant createdFrom,
        @RequestParam(value = "createdTo", required = false) Instant createdTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("entityNo", "currency", "clientId", "status", "factType", "createdFrom",
            "createdTo", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.searchBillingFacts(entityNo, currency, clientId, status, factType, createdFrom, createdTo,
            paging.page(), paging.size());
    }

    /** 登记计费事实（DRAFT）。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillingFactResponseBody submitBillingFact(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid SubmitBillingFactRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.submitBillingFact(context, body);
    }

    /** 计费事实详情：明细行、引用、状态与批准信息。 */
    @GetMapping("/{factId}")
    public BillingFactResponseBody getBillingFact(HttpServletRequest servletRequest,
        @PathVariable("factId") @Pattern(regexp = ID) String factId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getBillingFact(factId);
    }

    /** 批准计费事实；无请求体，携带 Body 时被请求边界拒绝。 */
    @PostMapping("/{factId}/approve")
    public BillingFactResponseBody approveBillingFact(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("factId") @Pattern(regexp = ID) String factId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.approveBillingFact(context, factId);
    }

    /** 作废计费事实草稿；无请求体，携带 Body 时被请求边界拒绝。 */
    @PostMapping("/{factId}/void")
    public BillingFactResponseBody voidBillingFact(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("factId") @Pattern(regexp = ID) String factId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.voidBillingFact(context, factId);
    }
}
