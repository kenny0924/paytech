package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ReconciliationClearingClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.AddClearingAccountMidRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ChangeClearingAccountStatusRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ClearingAccountResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ClearingAccountSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.CreateClearingAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.UnassignedMidListResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.UpdateClearingAccountSettingsRequestBody;
import com.paytech.operations.console.bff.service.processor.ReconClearingAccountDetailProcessor;
import com.paytech.operations.console.bff.service.processor.request.ReconClearingAccountDetailRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 对账清算账户的浏览器入口（块 123），除详情外简单转发 reconciliation ClearingAccountApi
 * <li>Gateway 绑定：查询 FINANCE_RECONCILIATION_READ；新建、配置、挂 / 摘 MID、启停 FINANCE_RECONCILIATION_OPERATE，并经 PLT-I02
 * 实时授权；BFF 不另判权限点。</li>
 * <li>详情由 ReconClearingAccountDetailProcessor 补 MID 外部编码与状态（channel-connector）。</li>
 * <li>浏览器路径为 /finance + reconciliation 路径；Query 按路由白名单严格匹配，GET 不接受 Body。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/reconciliation/v1/clearing-accounts")
public class ReconciliationClearingController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String ENTITY_NO = "[A-Z]{2}[0-9]{3}";
    private static final String CHANNEL_CODE = "[A-Z][A-Z0-9_]{1,63}";
    private static final String CURRENCY = "[A-Z]{3}";

    private final ReconciliationClearingClient client;
    private final ReconClearingAccountDetailProcessor detailProcessor;

    /** 清算账户分页，按编码升序。 */
    @GetMapping
    public PagingResponseBody<ClearingAccountSummaryResponseBody> listClearingAccounts(HttpServletRequest servletRequest,
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = ENTITY_NO) String entityNo,
        @RequestParam(value = "channelCode", required = false) @Pattern(regexp = CHANNEL_CODE) String channelCode,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = CURRENCY) String currency,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("entityNo", "channelCode", "currency", "status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listClearingAccounts(entityNo, channelCode, currency, status, paging.page(), paging.size());
    }

    /** 已激活但未归入清算账户的 MID + 币种。 */
    @GetMapping("/unassigned-mids")
    public UnassignedMidListResponseBody listUnassignedMids(HttpServletRequest servletRequest,
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = ENTITY_NO) String entityNo,
        @RequestParam(value = "channelCode", required = false) @Pattern(regexp = CHANNEL_CODE) String channelCode,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = CURRENCY) String currency) {
        requireQueryKeys(servletRequest, Set.of("entityNo", "channelCode", "currency"));
        return client.listUnassignedMids(entityNo, channelCode, currency);
    }

    /** 清算账户详情，MID 补外部编码与状态。 */
    @GetMapping("/{clearingAccountId}")
    public ClearingAccountResponseBody getClearingAccount(HttpServletRequest servletRequest,
        @PathVariable("clearingAccountId") @Pattern(regexp = ID) String clearingAccountId) {
        requireQueryKeys(servletRequest, Set.of());
        return detailProcessor.process(new ReconClearingAccountDetailRequest(clearingAccountId));
    }

    /** 新建清算账户。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClearingAccountResponseBody createClearingAccount(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid CreateClearingAccountRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createClearingAccount(context, body);
    }

    /** 整体替换三项配置与到账银行账户。 */
    @PutMapping("/{clearingAccountId}/settings")
    public ClearingAccountResponseBody updateClearingAccountSettings(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("clearingAccountId") @Pattern(regexp = ID) String clearingAccountId,
        @RequestBody @NotNull @Valid UpdateClearingAccountSettingsRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.updateClearingAccountSettings(context, clearingAccountId, body);
    }

    /** 挂入 MID。 */
    @PostMapping("/{clearingAccountId}/mids")
    public ClearingAccountResponseBody addClearingAccountMid(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("clearingAccountId") @Pattern(regexp = ID) String clearingAccountId,
        @RequestBody @NotNull @Valid AddClearingAccountMidRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.addClearingAccountMid(context, clearingAccountId, body);
    }

    /** 摘除 MID。 */
    @DeleteMapping("/{clearingAccountId}/mids/{midId}")
    public ClearingAccountResponseBody removeClearingAccountMid(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("clearingAccountId") @Pattern(regexp = ID) String clearingAccountId,
        @PathVariable("midId") @Pattern(regexp = ID) String midId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.removeClearingAccountMid(context, clearingAccountId, midId);
    }

    /** 启用或停用清算账户。 */
    @PostMapping("/{clearingAccountId}/status")
    public ClearingAccountResponseBody changeClearingAccountStatus(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("clearingAccountId") @Pattern(regexp = ID) String clearingAccountId,
        @RequestBody @NotNull @Valid ChangeClearingAccountStatusRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.changeClearingAccountStatus(context, clearingAccountId, body);
    }
}
