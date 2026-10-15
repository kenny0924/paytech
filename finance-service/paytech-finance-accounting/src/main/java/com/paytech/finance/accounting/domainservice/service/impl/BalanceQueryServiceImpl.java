package com.paytech.finance.accounting.domainservice.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.paytech.finance.accounting.core.constant.StandardAccountCodes;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.accounting.domain.AccountIdentityRule;
import com.paytech.finance.accounting.domain.model.ClientBalance;
import com.paytech.finance.accounting.domain.model.PartnerBalance;
import com.paytech.finance.accounting.domain.view.OwnerAccountBalanceView;
import com.paytech.finance.accounting.domainservice.repository.AccountRepository;
import com.paytech.finance.accounting.domainservice.repository.LedgerBookRepository;
import com.paytech.finance.accounting.domainservice.service.BalanceQueryService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.money.Money;

import lombok.AllArgsConstructor;

/**
 * 查询 Owner 的分类余额
 * <li>账户投影悬空、跨科目表或正常方向漂移时 Fail-Closed，不返回可能错误的余额。</li>
 * <li>Owner 全部账户由一条 SQL 读取，同一语句即一致快照；账簿身份不可变，因此不开启只读事务、不加锁。</li>
 */
@Service
@AllArgsConstructor
public class BalanceQueryServiceImpl implements BalanceQueryService {
    private final AccountRepository accountRepository;
    private final LedgerBookRepository ledgerBookRepository;

    /** 读取 Client 应付三桶、保证金和追偿应收。 */
    @Override
    public ClientBalance clientBalance(String clientId, String entityNo, String currency) {
        LedgerBook book = operatingEntityBook(entityNo, currency);
        Map<String, BigDecimal> balances = balances(book, OwnerType.CLIENT, clientId);
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
        LedgerBook book = operatingEntityBook(entityNo, currency);
        Map<String, BigDecimal> balances = balances(book, OwnerType.LOCAL_PARTNER, partnerId);
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

    /** 读取 OE 在指定币种下的唯一账簿；不存在时业务拒绝。 */
    LedgerBook operatingEntityBook(String entityNo, String currency) {
        AccountIdentityRule.checkEntityId(LedgerEntityType.OPERATING_ENTITY, entityNo);
        Money.zero(currency);
        LedgerBook book = ledgerBookRepository.findByEntity(LedgerEntityType.OPERATING_ENTITY, entityNo, currency);
        if (book == null) {
            throw new BizCheckFailException("未找到经营主体币种账簿: " + entityNo + "/" + currency);
        }
        return book;
    }

    /** 读取 Owner 账户并以“科目代码/余额桶”为键汇总；同键重复或投影不一致时 Fail-Closed。 */
    Map<String, BigDecimal> balances(LedgerBook book, OwnerType ownerType, String ownerId) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (OwnerAccountBalanceView account : accountRepository.findOwnerBalances(book.getId(), ownerType, ownerId)) {
            checkProjection(book, account);
            String key = key(account.getAccountCode(), account.getBalanceBucket());
            if (result.putIfAbsent(key, account.getNetBalance()) != null) {
                throw new ErrorException("余额账户投影不唯一: 账簿 " + book.getId() + ", " + key);
            }
        }
        return result;
    }

    /** 账户必须引用本账簿科目表下的可过账科目，且正常方向未漂移。 */
    void checkProjection(LedgerBook book, OwnerAccountBalanceView account) {
        if (account.getAccountCode() == null || !Boolean.TRUE.equals(account.getPostingAllowed())
            || !book.getChartId().equals(account.getAccountChartId())
            || account.getAccountNormalSide() != account.getChartNormalSide() || account.getNetBalance() == null) {
            throw new ErrorException("余额账户投影不完整或与账簿不一致: 账户 " + account.getAccountId());
        }
    }

    /** 读取指定科目与余额桶的金额，未建立账户时为零。 */
    Money money(String currency, Map<String, BigDecimal> balances, String accountCode, BalanceBucket bucket) {
        return Money.of(currency, balances.getOrDefault(key(accountCode, bucket), BigDecimal.ZERO));
    }

    /** 汇总键。 */
    String key(String accountCode, BalanceBucket bucket) {
        return accountCode + "/" + bucket.getCode();
    }
}
