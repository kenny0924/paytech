package com.paytech.client.console.bff.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.request.PayoutAccountCommandRequestBody;
import com.paytech.client.console.bff.service.model.request.PayoutSubmitRequestBody;
import com.paytech.client.console.bff.service.processor.CurrentWorkspaceResolver;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.payout.api.balance.BalanceExplanationApi;
import com.paytech.payout.api.balance.response.BalanceExplanationResponseBody;
import com.paytech.payout.api.beneficiary.BeneficiaryAccountApi;
import com.paytech.payout.api.beneficiary.BeneficiaryRequirementApi;
import com.paytech.payout.api.beneficiary.request.BeneficiaryAccountCommandRequestBody;
import com.paytech.payout.api.beneficiary.request.SubmitBeneficiaryAccountRequestBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountListResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryRequirementResponseBody;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * 工作区“资金”接口（technique 8.4，/client-payout/v1）：收款账户与余额解释的薄转发
 * <li>租户上下文、权限、路径 ID、Query 白名单与空 Body 由 {@code ClientPayoutWebConfiguration} 先行判断。</li>
 * <li>Owner 固定为 Platform 成员事实给出的当前工作区 Client，操作人为当前成员账号；浏览器不能提交 Owner 或操作人。</li>
 * <li>payout 以 Owner 校验详情、设默认与关闭的账户归属（不符 404）；运营专属的共用提示与原值不对 Client 返回。</li>
 * <li>余额只有 OE 编号：Client 侧没有可读取 OE 显示名的 Owner 合同。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/client-payout/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientPayoutController {
    private static final String CLIENT = BeneficiaryOwnerType.CLIENT.getCode();

    private final BeneficiaryAccountApi accounts;
    private final BalanceExplanationApi balances;
    private final BeneficiaryRequirementApi requirements;
    private final CurrentWorkspaceResolver workspaces;

    /** 当前工作区的收款账户（CLIENT_PAYOUT_ACCOUNT_READ）。 */
    @GetMapping("/beneficiary-accounts")
    public BeneficiaryAccountListResponseBody listBeneficiaryAccounts(ClientActor actor,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "currency", required = false) String currency) {
        return accounts.listBeneficiaryAccounts(CallerServiceCodes.CLIENT_CONSOLE_BFF, CLIENT, clientId(actor), status, currency);
    }

    /** 提交收款账户（CLIENT_PAYOUT_ACCOUNT_MANAGE）；保持 payout 的 201 / 200。 */
    @PostMapping("/beneficiary-accounts")
    public ResponseEntity<BeneficiaryAccountResponseBody> submitBeneficiaryAccount(ClientActor actor,
        @Valid @RequestBody PayoutSubmitRequestBody body) {
        var request = new SubmitBeneficiaryAccountRequestBody();
        request.setOwnerType(CLIENT);
        request.setOwnerId(clientId(actor));
        request.setRailType(body.getRailType());
        request.setBankCountry(body.getBankCountry());
        request.setCurrency(body.getCurrency());
        request.setFields(body.getFields());
        request.setActorType(BeneficiaryActorType.CLIENT_MEMBER.getCode());
        request.setActorId(actor.getAccountId());
        var response = accounts.submitBeneficiaryAccount(request);
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    /** 收款账户详情与时间线（CLIENT_PAYOUT_ACCOUNT_READ）；不属于当前工作区时 404。 */
    @GetMapping("/beneficiary-accounts/{accountId}")
    public BeneficiaryAccountResponseBody getBeneficiaryAccount(ClientActor actor, @PathVariable("accountId") String accountId) {
        return accounts.getBeneficiaryAccount(CallerServiceCodes.CLIENT_CONSOLE_BFF, accountId, CLIENT, clientId(actor));
    }

    /** 设为同币种默认账户（CLIENT_PAYOUT_ACCOUNT_MANAGE）。 */
    @PostMapping("/beneficiary-accounts/{accountId}/default")
    public BeneficiaryAccountResponseBody setDefaultBeneficiaryAccount(ClientActor actor,
        @PathVariable("accountId") String accountId, @Valid @RequestBody PayoutAccountCommandRequestBody body) {
        return accounts.setDefaultBeneficiaryAccount(accountId, command(actor, body));
    }

    /** 停止使用（关闭）收款账户（CLIENT_PAYOUT_ACCOUNT_MANAGE）。 */
    @PostMapping("/beneficiary-accounts/{accountId}/close")
    public BeneficiaryAccountResponseBody closeBeneficiaryAccount(ClientActor actor,
        @PathVariable("accountId") String accountId, @Valid @RequestBody PayoutAccountCommandRequestBody body) {
        return accounts.closeBeneficiaryAccount(accountId, command(actor, body));
    }

    /** 添加向导的字段要求（CLIENT_PAYOUT_ACCOUNT_READ）；组合不支持时 404。 */
    @GetMapping("/beneficiary-requirements")
    public BeneficiaryRequirementResponseBody getBeneficiaryRequirement(@RequestParam("railType") String railType,
        @RequestParam("bankCountry") String bankCountry, @RequestParam("currency") String currency) {
        return requirements.getBeneficiaryRequirement(railType, bankCountry, currency);
    }

    /** 当前工作区各 OE × 币种的余额与不能提款的原因（CLIENT_BALANCE_READ）。 */
    @GetMapping("/balances")
    public BalanceExplanationResponseBody explainBalance(ClientActor actor) {
        String clientId = clientId(actor);
        var result = balances.explainBalance(CLIENT, clientId);
        if (result == null || !CLIENT.equals(result.getOwnerType()) || !clientId.equals(result.getOwnerId())) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return result;
    }

    /** 当前工作区的 Client ID（Platform 成员事实）。 */
    String clientId(ClientActor actor) {
        return workspaces.current(actor).getClientId();
    }

    /** 带当前工作区 Owner 与成员操作人的账户命令。 */
    BeneficiaryAccountCommandRequestBody command(ClientActor actor, PayoutAccountCommandRequestBody body) {
        var request = new BeneficiaryAccountCommandRequestBody();
        request.setOwnerType(CLIENT);
        request.setOwnerId(clientId(actor));
        request.setReasonCode(body.getReasonCode());
        request.setExpectedVersion(body.getExpectedVersion());
        request.setActorType(BeneficiaryActorType.CLIENT_MEMBER.getCode());
        request.setActorId(actor.getAccountId());
        return request;
    }
}
