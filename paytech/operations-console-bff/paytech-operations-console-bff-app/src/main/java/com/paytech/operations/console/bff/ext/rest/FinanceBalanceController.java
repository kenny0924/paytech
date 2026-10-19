package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.FinanceBalanceClient;
import com.paytech.operations.console.bff.service.model.FinanceBalanceModels.ClientBalanceResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBalanceModels.PartnerBalanceResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * Owner 余额查询的浏览器入口，简单转发 account
 * <li>路由由 Gateway 绑定 FINANCE_ACCOUNTING_READ 并经 PLT-I02 实时授权；Available 不等于可出款资格。</li>
 * <li>浏览器路径为 /finance + account 路径；entityNo 与 currency 必填。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/balances/v1")
public class FinanceBalanceController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String ENTITY_NO = "[A-Z]{2}[0-9]{3}";
    private static final String CURRENCY = "[A-Z]{3}";

    private final FinanceBalanceClient client;

    /** R17 Client 在一个 Operating Entity 与币种下的分类余额。 */
    @GetMapping("/clients/{clientId}")
    public ClientBalanceResponseBody queryClientBalance(HttpServletRequest servletRequest,
        @PathVariable("clientId") @Pattern(regexp = ID) String clientId,
        @RequestParam("entityNo") @Pattern(regexp = ENTITY_NO) String entityNo,
        @RequestParam("currency") @Pattern(regexp = CURRENCY) String currency) {
        requireQueryKeys(servletRequest, Set.of("entityNo", "currency"));
        return client.queryClientBalance(clientId, entityNo, currency);
    }

    /** R18 Local Partner 在一个 Operating Entity 与币种下的分类余额，不含保证金。 */
    @GetMapping("/partners/{partnerId}")
    public PartnerBalanceResponseBody queryPartnerBalance(HttpServletRequest servletRequest,
        @PathVariable("partnerId") @Pattern(regexp = ID) String partnerId,
        @RequestParam("entityNo") @Pattern(regexp = ENTITY_NO) String entityNo,
        @RequestParam("currency") @Pattern(regexp = CURRENCY) String currency) {
        requireQueryKeys(servletRequest, Set.of("entityNo", "currency"));
        return client.queryPartnerBalance(partnerId, entityNo, currency);
    }
}
