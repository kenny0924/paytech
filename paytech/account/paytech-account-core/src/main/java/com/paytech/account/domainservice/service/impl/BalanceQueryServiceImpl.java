package com.paytech.account.domainservice.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.domain.model.ClientBalance;
import com.paytech.account.domain.model.PartnerBalance;
import com.paytech.account.domainservice.repository.OwnerAccountRepository;
import com.paytech.account.domainservice.service.BalanceQueryService;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.money.Money;

import lombok.AllArgsConstructor;

/**
 * 查询 Owner 的分类余额
 * <li>只读 mor_account 的 Owner 账户（technique 2.2 BalanceQueryApi）；Owner 的全部账户由一条 SQL 读取，即一致快照，不加锁、不开事务。</li>
 * <li>单个主体与币种：没有任何账户时返回空，由调用方拒绝；有账户但缺少某个桶时该金额为零。</li>
 * <li>全部主体与币种（positions）：一条 SQL 读取该 Owner 的经营主体账户后按 entityNo × 币种分组，没有账户时为空列表。</li>
 * <li>账户身份漂移（同键重复或金额缺失）时 Fail-Closed，不返回可能错误的余额。</li>
 */
@Service
@AllArgsConstructor
public class BalanceQueryServiceImpl implements BalanceQueryService {
    private final OwnerAccountRepository ownerAccountRepository;

    /** 读取 Client 应付三桶、保证金和追偿应收。 */
    @Override
    public ClientBalance clientBalance(String clientId, String entityNo, String currency) {
        Map<String, BigDecimal> balances = balances(entityNo, currency, OwnerType.CLIENT, clientId);
        if (balances.isEmpty()) {
            return null;
        }
        return clientBalance(clientId, entityNo, currency, balances);
    }

    /** 读取 Partner 应付三桶和追偿应收。 */
    @Override
    public PartnerBalance partnerBalance(String partnerId, String entityNo, String currency) {
        Map<String, BigDecimal> balances = balances(entityNo, currency, OwnerType.LOCAL_PARTNER, partnerId);
        if (balances.isEmpty()) {
            return null;
        }
        return partnerBalance(partnerId, entityNo, currency, balances);
    }

    /** 一条 SQL 读取 Client 全部经营主体账户，按 entityNo × 币种分组。 */
    @Override
    public List<ClientBalance> clientPositions(String clientId) {
        return positions(OwnerType.CLIENT, clientId).entrySet().stream()
            .map(position -> clientBalance(clientId, position.getKey().entityNo(), position.getKey().currency(),
                position.getValue()))
            .toList();
    }

    /** 一条 SQL 读取 Partner 全部经营主体账户，按 entityNo × 币种分组。 */
    @Override
    public List<PartnerBalance> partnerPositions(String partnerId) {
        return positions(OwnerType.LOCAL_PARTNER, partnerId).entrySet().stream()
            .map(position -> partnerBalance(partnerId, position.getKey().entityNo(), position.getKey().currency(),
                position.getValue()))
            .toList();
    }

    /** 由已汇总的“科目代码/余额桶”金额组装 Client 分类余额。 */
    ClientBalance clientBalance(String clientId, String entityNo, String currency, Map<String, BigDecimal> balances) {
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

    /** 由已汇总的“科目代码/余额桶”金额组装 Partner 分类余额。 */
    PartnerBalance partnerBalance(String partnerId, String entityNo, String currency, Map<String, BigDecimal> balances) {
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

    /** 读取 Owner 在一个主体与币种下的账户并以“科目代码/余额桶”为键汇总；同键重复或金额缺失时 Fail-Closed。 */
    Map<String, BigDecimal> balances(String entityNo, String currency, OwnerType ownerType, String ownerId) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (OwnerAccount account : ownerAccountRepository.findByOwner(AccountEntityType.OPERATING_ENTITY, entityNo,
            currency, ownerType, ownerId)) {
            accumulate(result, account);
        }
        return result;
    }

    /** 读取 Owner 全部经营主体账户，按 entityNo × 币种分组后各自汇总；顺序为 entityNo、币种升序。 */
    Map<Position, Map<String, BigDecimal>> positions(OwnerType ownerType, String ownerId) {
        Map<Position, Map<String, BigDecimal>> result = new TreeMap<>(
            Comparator.comparing(Position::entityNo).thenComparing(Position::currency));
        for (OwnerAccount account : ownerAccountRepository.findByOwnerAcrossEntities(AccountEntityType.OPERATING_ENTITY,
            ownerType, ownerId)) {
            accumulate(result.computeIfAbsent(new Position(account.getEntityId(), account.getCurrency()),
                position -> new HashMap<>()), account);
        }
        return result;
    }

    /** 把一个账户的净余额记入所属主体与币种的汇总；同键重复或金额缺失时 Fail-Closed。 */
    void accumulate(Map<String, BigDecimal> balances, OwnerAccount account) {
        if (account.getAccountCode() == null || account.getNetBalance() == null) {
            throw new ErrorException("Owner 账户不完整: " + account.getId());
        }
        String key = key(account.getAccountCode(), account.getBalanceBucket());
        if (balances.putIfAbsent(key, account.getNetBalance()) != null) {
            throw new ErrorException(
                "Owner 账户不唯一: " + account.getEntityId() + "/" + account.getCurrency() + ", " + key);
        }
    }

    /** 读取指定科目与余额桶的金额，未开立该桶账户时为零。 */
    Money money(String currency, Map<String, BigDecimal> balances, String accountCode, BalanceBucket bucket) {
        return Money.of(currency, balances.getOrDefault(key(accountCode, bucket), BigDecimal.ZERO));
    }

    /** 汇总键。 */
    String key(String accountCode, BalanceBucket bucket) {
        return accountCode + "/" + bucket.getCode();
    }

    /**
     * 经营主体 × 币种分组键
     *
     * @param entityNo 经营主体编号
     * @param currency 币种
     */
    record Position(String entityNo, String currency) {
    }
}
