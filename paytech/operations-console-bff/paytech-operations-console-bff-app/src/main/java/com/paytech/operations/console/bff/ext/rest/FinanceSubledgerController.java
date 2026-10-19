package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;
import static com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.BALANCE_BUCKET_PATTERN;
import static com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.ENTITY_TYPE_PATTERN;
import static com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OWNER_TYPE_PATTERN;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.FinanceSubledgerClient;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.AccountPostingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountEntryResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OwnerAccountResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 明细账查询（Owner 账户、明细流水、过账批次）的浏览器入口，全部简单转发 account
 * <li>路由由 Gateway 绑定 FINANCE_ACCOUNTING_READ 并经 PLT-I02 实时授权；只读，不暴露过账进度、追平读取与按流重建。</li>
 * <li>浏览器路径为 /finance + account 路径；Query 按路由白名单严格匹配，GET 不接受 Body。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance")
public class FinanceSubledgerController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final FinanceSubledgerClient client;

    /** R19 主体与币种下的 Owner 账户及余额分页；主体类型、主体标识与币种必填。 */
    @GetMapping("/owner-accounts/v1")
    public PagingResponseBody<OwnerAccountResponseBody> listOwnerAccounts(HttpServletRequest servletRequest,
        @RequestParam("entityType") @Pattern(regexp = ENTITY_TYPE_PATTERN) String entityType,
        @RequestParam("entityId") @Pattern(regexp = "[A-Z0-9]{1,19}") String entityId,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "ownerType", required = false) @Pattern(regexp = OWNER_TYPE_PATTERN) String ownerType,
        @RequestParam(value = "ownerId", required = false) @Pattern(regexp = ID) String ownerId,
        @RequestParam(value = "accountCode", required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String accountCode,
        @RequestParam(value = "balanceBucket", required = false) @Pattern(regexp = BALANCE_BUCKET_PATTERN) String balanceBucket,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("entityType", "entityId", "currency", "ownerType", "ownerId",
            "accountCode", "balanceBucket", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listOwnerAccounts(entityType, entityId, currency, ownerType, ownerId, accountCode, balanceBucket,
            paging.page(), paging.size());
    }

    /** R20 Owner 账户明细流水分页，按批次序号、行号升序。 */
    @GetMapping("/owner-accounts/v1/{accountId}/entries")
    public PagingResponseBody<OwnerAccountEntryResponseBody> listOwnerAccountEntries(HttpServletRequest servletRequest,
        @PathVariable("accountId") @Pattern(regexp = ID) String accountId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listOwnerAccountEntries(accountId, paging.page(), paging.size());
    }

    /** R21 过账批次详情：批次头与全部流水。 */
    @GetMapping("/account-postings/v1/{postingId}")
    public AccountPostingResponseBody getPosting(HttpServletRequest servletRequest,
        @PathVariable("postingId") @Pattern(regexp = ID) String postingId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getPosting(postingId);
    }
}
