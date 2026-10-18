package com.paytech.account.domainservice.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domain.model.ClientBalance;
import com.paytech.account.domain.model.PartnerBalance;
import com.paytech.account.domainservice.repository.AccountBalanceRepository;
import com.paytech.account.domainservice.service.BalanceQueryService;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.money.Money;

import lombok.AllArgsConstructor;

/**
 * 查询 Owner 的分类余额
 * <li>读取 mor_account 自有余额（design/04 §6）；Owner 的全部余额由一条 SQL 读取，即一致快照，不加锁、不开事务。</li>
 * <li>余额身份漂移（同键重复或金额缺失）时 Fail-Closed，不返回可能错误的余额；账簿存在性由调用方事务外经 ledger-api 先行确认。</li>
 */
@Service
@AllArgsConstructor
public class BalanceQueryServiceImpl implements BalanceQueryService {
    private final AccountBalanceRepository accountBalanceRepository;

    /** 读取 Client 应付三桶、保证金和追偿应收。 */
    @Override
    public ClientBalance clientBalance(String clientId, String entityNo, String currency) {
        Map<String, BigDecimal> balances = balances(BookIdentity.operatingEntity(entityNo, currency), OwnerType.CLIENT,
            clientId);
        ClientBalance balance = new ClientBalance();
        balance.setClientId(clientId);
        balance.setEntityNo(entityNo);
        balance.setCurrency(currency);
        balance.setPendingAmount(money(currency, balances, StandardAccountCodes.CLIENT_PAYABLE, BalanceBucket.PENDING));
        balance.setAvailableAmount(money(currency, balances, StandardAccountCodes.CLIENT_PAYABLE, BalanceBucket.AVAILABLE));
        balance.setLockedAmount(money(currency, balances, StandardAccountCodes.CLIENT_PAYABLE, BalanceBucket.LOCKED));
        balance.setSecurityDepositAmount(
            money(currency, balances, StandardAccountCodes.CLIENT_SECURITY_DEPOSIT, BalanceBucket.NONE));
        balance.setRecoveryReceivableAmount(
            money(currency, balances, StandardAccountCodes.CLIENT_RECOVERY_RECEIVABLE, BalanceBucket.NONE));
        return balance;
    }

    /** 读取 Partner 应付三桶和追偿应收。 */
    @Override
    public PartnerBalance partnerBalance(String partnerId, String entityNo, String currency) {
        Map<String, BigDecimal> balances = balances(BookIdentity.operatingEntity(entityNo, currency),
            OwnerType.LOCAL_PARTNER, partnerId);
        PartnerBalance balance = new PartnerBalance();
        balance.setPartnerId(partnerId);
        balance.setEntityNo(entityNo);
        balance.setCurrency(currency);
        balance.setPendingAmount(money(currency, balances, StandardAccountCodes.PARTNER_PAYABLE, BalanceBucket.PENDING));
        balance.setAvailableAmount(money(currency, balances, StandardAccountCodes.PARTNER_PAYABLE, BalanceBucket.AVAILABLE));
        balance.setLockedAmount(money(currency, balances, StandardAccountCodes.PARTNER_PAYABLE, BalanceBucket.LOCKED));
        balance.setRecoveryReceivableAmount(
            money(currency, balances, StandardAccountCodes.PARTNER_RECOVERY_RECEIVABLE, BalanceBucket.NONE));
        return balance;
    }

    /** 读取 Owner 余额并以“科目代码/余额桶”为键汇总；同键重复或金额缺失时 Fail-Closed。 */
    Map<String, BigDecimal> balances(BookIdentity book, OwnerType ownerType, String ownerId) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (AccountBalance balance : accountBalanceRepository.findByOwner(book, ownerType, ownerId)) {
            if (balance.getAccountCode() == null || balance.getNetBalance() == null) {
                throw new ErrorException("余额账户不完整: " + balance.getId());
            }
            String key = key(balance.getAccountCode(), balance.getBalanceBucket());
            if (result.putIfAbsent(key, balance.getNetBalance()) != null) {
                throw new ErrorException("余额账户不唯一: " + book + ", " + key);
            }
        }
        return result;
    }

    /** 读取指定科目与余额桶的金额，未建立余额时为零。 */
    Money money(String currency, Map<String, BigDecimal> balances, String accountCode, BalanceBucket bucket) {
        return Money.of(currency, balances.getOrDefault(key(accountCode, bucket), BigDecimal.ZERO));
    }

    /** 汇总键。 */
    String key(String accountCode, BalanceBucket bucket) {
        return accountCode + "/" + bucket.getCode();
    }
}
