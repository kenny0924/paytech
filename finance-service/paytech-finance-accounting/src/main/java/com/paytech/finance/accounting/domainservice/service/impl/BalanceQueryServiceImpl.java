package com.paytech.finance.accounting.domainservice.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.paytech.finance.accounting.core.dal.dataobject.BalanceAccountRow;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBookRow;
import com.paytech.finance.accounting.domain.constants.StandardAccountCodes;
import com.paytech.finance.accounting.domain.enums.BalanceBucket;
import com.paytech.finance.accounting.domain.enums.OwnerType;
import com.paytech.finance.accounting.domain.model.AccountBalanceKey;
import com.paytech.finance.accounting.domain.model.ClientBalance;
import com.paytech.finance.accounting.domain.model.PartnerBalance;
import com.paytech.finance.accounting.domainservice.repository.BalanceAccountRepository;
import com.paytech.finance.accounting.domainservice.service.BalanceQueryService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;

import lombok.RequiredArgsConstructor;

/** 查询 Owner 的分类余额。 */
@Service
@RequiredArgsConstructor
public class BalanceQueryServiceImpl implements BalanceQueryService {
    private final BalanceAccountRepository repository;

    @Override
    public ClientBalance clientBalance(String clientId, String operatingEntityId, String currency) {
        long parsedClientId = positiveId(clientId);
        long parsedOperatingEntityId = positiveId(operatingEntityId);
        validateCurrency(currency);
        LedgerBookRow book = uniqueBook(parsedOperatingEntityId, currency);
        Map<AccountBalanceKey, BigDecimal> balances = balances(book, OwnerType.CLIENT, parsedClientId);
        return new ClientBalance(parsedClientId, parsedOperatingEntityId, currency,
            money(currency, balances, StandardAccountCodes.CLIENT_PAYABLE, BalanceBucket.PENDING),
            money(currency, balances, StandardAccountCodes.CLIENT_PAYABLE, BalanceBucket.AVAILABLE),
            money(currency, balances, StandardAccountCodes.CLIENT_PAYABLE, BalanceBucket.LOCKED),
            money(currency, balances, StandardAccountCodes.CLIENT_SECURITY_DEPOSIT, BalanceBucket.NONE),
            money(currency, balances, StandardAccountCodes.CLIENT_RECOVERY_RECEIVABLE, BalanceBucket.NONE));
    }

    @Override
    public PartnerBalance partnerBalance(String partnerId, String operatingEntityId, String currency) {
        long parsedPartnerId = positiveId(partnerId);
        long parsedOperatingEntityId = positiveId(operatingEntityId);
        validateCurrency(currency);
        LedgerBookRow book = uniqueBook(parsedOperatingEntityId, currency);
        Map<AccountBalanceKey, BigDecimal> balances = balances(book, OwnerType.LOCAL_PARTNER, parsedPartnerId);
        return new PartnerBalance(parsedPartnerId, parsedOperatingEntityId, currency,
            money(currency, balances, StandardAccountCodes.PARTNER_PAYABLE, BalanceBucket.PENDING),
            money(currency, balances, StandardAccountCodes.PARTNER_PAYABLE, BalanceBucket.AVAILABLE),
            money(currency, balances, StandardAccountCodes.PARTNER_PAYABLE, BalanceBucket.LOCKED),
            money(currency, balances, StandardAccountCodes.PARTNER_RECOVERY_RECEIVABLE, BalanceBucket.NONE));
    }

    private LedgerBookRow uniqueBook(long operatingEntityId, String currency) {
        List<LedgerBookRow> books = repository.operatingEntityBooks(operatingEntityId, currency);
        if (books.isEmpty()) {
            throw new BizCheckFailException("未找到指定经营主体和币种的账簿");
        }
        if (books.size() != 1) {
            throw new ErrorException("经营主体币种账簿不唯一");
        }
        return books.getFirst();
    }

    private Map<AccountBalanceKey, BigDecimal> balances(LedgerBookRow book, OwnerType ownerType, long ownerId) {
        Map<AccountBalanceKey, BigDecimal> result = new HashMap<>();
        for (BalanceAccountRow account : repository.ownerAccounts(book.id(), ownerType, ownerId)) {
            validateAccount(book, account);
            AccountBalanceKey key = new AccountBalanceKey(account.accountCode(), account.balanceBucket());
            if (result.putIfAbsent(key, account.netBalance()) != null) {
                throw new ErrorException("余额账户投影不唯一");
            }
        }
        return result;
    }

    private void validateAccount(LedgerBookRow book, BalanceAccountRow account) {
        if (account.accountCode() == null || account.accountChartId() == null || account.postingAllowed() == null
            || !account.postingAllowed() || account.accountChartId() != book.chartId()
            || account.accountNormalSide() != account.chartNormalSide() || account.netBalance() == null) {
            throw new ErrorException("余额账户投影不完整或与账簿不一致");
        }
    }

    private Money money(String currency, Map<AccountBalanceKey, BigDecimal> balances, String accountCode,
        BalanceBucket balanceBucket) {
        return Money.of(currency,
            balances.getOrDefault(new AccountBalanceKey(accountCode, balanceBucket), BigDecimal.ZERO));
    }

    private long positiveId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 统一转换为安全、稳定的参数错误，不外显原始输入。
        }
        throw new InvalidParameterException("业务标识必须为正整数且不超过 BIGINT 范围");
    }

    private void validateCurrency(String currency) {
        Money.zero(currency);
    }
}
