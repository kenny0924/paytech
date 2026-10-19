package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;
import static com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.LEDGER_OWNER_TYPE_PATTERN;

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
import com.paytech.operations.console.bff.integration.FinanceLedgerBookClient;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.AccountJournalLineResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerAccountRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerBookRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.CreateLedgerBookResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.JournalEntryDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.LedgerAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.LedgerBookResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * 账簿、账户、账户流水与分录详情的浏览器入口，全部简单转发 ledger
 * <li>每个路由由 Gateway 绑定 FINANCE_ACCOUNTING_READ（GET）或 FINANCE_ACCOUNTING_MANAGE（POST），并经 PLT-I02 实时授权。</li>
 * <li>浏览器路径为 /finance + ledger 路径；Query 按路由白名单严格匹配，GET 不接受 Body。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/ledger-books/v1")
public class FinanceLedgerBookController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String DATE = "[0-9]{4}-[0-9]{2}-[0-9]{2}";

    private final FinanceLedgerBookClient client;

    /** R08 账簿分页，按主键升序。 */
    @GetMapping
    public PagingResponseBody<LedgerBookResponseBody> listLedgerBooks(HttpServletRequest servletRequest,
        @RequestParam(value = "entityType", required = false) @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam(value = "entityId", required = false) @Size(max = 64) String entityId,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("entityType", "entityId", "currency", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listLedgerBooks(entityType, entityId, currency, paging.page(), paging.size());
    }

    /** R09 新建 OPEN 账簿。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateLedgerBookResponseBody createLedgerBook(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid CreateLedgerBookRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createLedgerBook(context, body);
    }

    /** R10 账簿内账户及余额分页；核算对象只有平台类对象，Client 与 Local Partner 明细见明细账查询。 */
    @GetMapping("/{ledgerBookId}/accounts")
    public PagingResponseBody<LedgerAccountResponseBody> listAccounts(HttpServletRequest servletRequest,
        @PathVariable("ledgerBookId") @Pattern(regexp = ID) String ledgerBookId,
        @RequestParam(value = "accountCode", required = false) @Size(max = 64) String accountCode,
        @RequestParam(value = "ownerType", required = false) @Pattern(regexp = LEDGER_OWNER_TYPE_PATTERN) String ownerType,
        @RequestParam(value = "ownerId", required = false) @Size(max = 64) String ownerId,
        @RequestParam(value = "balanceBucket", required = false) @Pattern(regexp = "PENDING|AVAILABLE|LOCKED|NONE") String balanceBucket,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("accountCode", "ownerType", "ownerId", "balanceBucket", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listAccounts(ledgerBookId, accountCode, ownerType, ownerId, balanceBucket, paging.page(),
            paging.size());
    }

    /** R11 在账簿下新建 ACTIVE 账户。 */
    @PostMapping("/{ledgerBookId}/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateLedgerAccountResponseBody createAccount(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("ledgerBookId") @Pattern(regexp = ID) String ledgerBookId,
        @RequestBody @NotNull @Valid CreateLedgerAccountRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createAccount(context, ledgerBookId, body);
    }

    /** R12 账户流水分页，按分录序号、行号升序；行后余额按全量历史累计。 */
    @GetMapping("/{ledgerBookId}/accounts/{accountId}/journal-lines")
    public PagingResponseBody<AccountJournalLineResponseBody> listAccountJournalLines(HttpServletRequest servletRequest,
        @PathVariable("ledgerBookId") @Pattern(regexp = ID) String ledgerBookId,
        @PathVariable("accountId") @Pattern(regexp = ID) String accountId,
        @RequestParam(value = "postingDateFrom", required = false) @Pattern(regexp = DATE) String postingDateFrom,
        @RequestParam(value = "postingDateTo", required = false) @Pattern(regexp = DATE) String postingDateTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("postingDateFrom", "postingDateTo", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listAccountJournalLines(ledgerBookId, accountId, postingDateFrom, postingDateTo, paging.page(),
            paging.size());
    }

    /** R13 分录详情。 */
    @GetMapping("/{ledgerBookId}/journal-entries/{journalEntryId}")
    public JournalEntryDetailResponseBody getJournalEntry(HttpServletRequest servletRequest,
        @PathVariable("ledgerBookId") @Pattern(regexp = ID) String ledgerBookId,
        @PathVariable("journalEntryId") @Pattern(regexp = ID) String journalEntryId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getJournalEntry(ledgerBookId, journalEntryId);
    }
}
