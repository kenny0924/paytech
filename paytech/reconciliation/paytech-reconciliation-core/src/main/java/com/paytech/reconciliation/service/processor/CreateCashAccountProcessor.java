package com.paytech.reconciliation.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.domain.CashAccountReviews;
import com.paytech.reconciliation.domain.ReconResolutionRules;
import com.paytech.reconciliation.domainservice.service.CashAccountService;
import com.paytech.reconciliation.service.processor.request.CreateCashAccountRequest;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;
import lombok.AllArgsConstructor;

/**
 * 提交新建银行账户（UC-R02，#7，technique D-03、D-04）：Vault Protect 后建 PENDING_VERIFICATION 账户，等待复核
 * <li>主体标识须与主体类型相符（经营主体为 entityNo，财务主体为正整数）；本期不向 Owner 核对主体是否存在。</li>
 * <li>银行账号在事务外经 Vault Protect（CASH_ACCOUNT_NUMBER_V1、REFERENCE、用途 RECON_CASH_ACCOUNT），幂等键由提交人与全部输入派生，
 * 同一命令重试得到同一 Token；本库只存 Token 与本服务算出的末四位，明文不落库、不进日志。</li>
 * <li>同主体 + 币种 + 用途只允许一个 ACTIVE，在复核通过时校验；没有业务唯一身份，不做原命令比较。</li>
 */
@Component
@AllArgsConstructor
public class CreateCashAccountProcessor extends AbstractProcessor<CreateCashAccountRequest, CashAccount> {
    /** Vault 数据类型。 */
    static final String DATA_TYPE = "CASH_ACCOUNT_NUMBER_V1";

    /** Vault Protect 用途。 */
    static final String PURPOSE = "RECON_CASH_ACCOUNT";

    /** Vault 幂等键前缀。 */
    static final String KEY_PREFIX = "recon-cash-account.";

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final VaultClient vaultClient;
    private final CashAccountService cashAccountService;

    @Override
    protected CashAccount processRequest(CreateCashAccountRequest request) {
        // 业务验证
        checkEntity(request);

        // 事务外 Vault Protect
        String token = vaultClient.protect(new ProtectCommand(DATA_TYPE, TokenMode.REFERENCE, request.getAccountNumber(),
            PURPOSE, idempotencyKey(request))).token();

        // 本地保存
        CashAccount account = build(request, token);
        cashAccountService.create(account);
        return account;
    }

    /** 主体标识与主体类型相符。 */
    static void checkEntity(CreateCashAccountRequest request) {
        String pattern = request.getEntityType() == ReconEntityType.OPERATING_ENTITY ? "[A-Z]{2}[0-9]{3}" : "[1-9][0-9]{0,18}";
        if (!request.getEntityId().matches(pattern)) {
            throw new BizCheckFailException("主体标识与主体类型不符: " + request.getEntityType().getCode() + " "
                + request.getEntityId());
        }
    }

    /** Vault 幂等键：提交人与全部输入的 SHA-256（不含可读明文）。 */
    static String idempotencyKey(CreateCashAccountRequest request) {
        return KEY_PREFIX + ReconResolutionRules.sha256(String.join("\n", request.getActorId().toString(),
            request.getEntityType().getCode(), request.getEntityId(), request.getAccountPurpose().getCode(),
            request.getCurrency(), request.getBankCode(), request.getAccountName(), request.getAccountNumber()));
    }

    /** PENDING_VERIFICATION 新账户；内容摘要覆盖本次提交复核的全部内容。 */
    CashAccount build(CreateCashAccountRequest request, String token) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String number = request.getAccountNumber();
        CashAccount account = new CashAccount();
        account.setId(idGenerator.nextId());
        account.setEntityType(request.getEntityType());
        account.setEntityId(request.getEntityId());
        account.setAccountPurpose(request.getAccountPurpose());
        account.setBankCode(request.getBankCode());
        account.setAccountName(request.getAccountName());
        account.setCurrency(request.getCurrency());
        account.setAccountVaultToken(token);
        account.setAccountLast4(number.substring(number.length() - 4).toUpperCase(Locale.ROOT));
        account.setStatus(CashAccountStatus.PENDING_VERIFICATION);
        account.setMakerId(request.getActorId());
        account.setContentHash(CashAccountReviews.createHash(account));
        account.setVersion(1L);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return account;
    }
}
