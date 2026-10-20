package com.paytech.account.test.mocker.local;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.core.enums.AccountStatus;
import com.paytech.account.domainservice.process.AccountPostingWriter;
import com.paytech.account.test.dal.AccountTestMapper;

/**
 * Owner 账户前置数据：本期没有事实来源的账户（如 BANK_CASH 银行现金容量）与账户状态由测试直接插入或修改
 * <li>只插入账户行，不写流水；余额按正常方向记在借方或贷方合计。主键在测试进程内递增，避免与 Snowflake 主键冲突。</li>
 * <li>账户号按生产格式组装，序号取主键（12 位），不占用账户号序列，也不会与序列分配的小序号冲突。</li>
 */
@TestComponent
public class OwnerAccountMocker {
    private static final AtomicLong IDS = new AtomicLong(990_000_000_000L);

    private final AccountTestMapper mapper;

    public OwnerAccountMocker(AccountTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 插入一个 OE 主体下的 ACTIVE 账户
     *
     * @param entityNo 经营主体编号
     * @param currency 币种
     * @param type 账户类型
     * @param ownerId 核算对象标识
     * @param bucket 余额桶
     * @param netBalance 按正常方向的净余额
     * @return 账户主键
     */
    public long mockAccount(String entityNo, String currency, OwnerAccountType type, String ownerId, BalanceBucket bucket,
        String netBalance) {
        return mockAccount(AccountEntityType.OPERATING_ENTITY, entityNo, currency, type, ownerId, bucket, netBalance);
    }

    /**
     * 插入一个指定主体下的 ACTIVE 账户
     *
     * @param entityType 主体类型
     * @param entityId 主体标识
     * @param currency 币种
     * @param type 账户类型
     * @param ownerId 核算对象标识
     * @param bucket 余额桶
     * @param netBalance 按正常方向的净余额
     * @return 账户主键
     */
    public long mockAccount(AccountEntityType entityType, String entityId, String currency, OwnerAccountType type,
        String ownerId, BalanceBucket bucket, String netBalance) {
        long id = IDS.incrementAndGet();
        BigDecimal net = new BigDecimal(netBalance);
        boolean debitNormal = type.getNormalSide() == EntrySide.DEBIT;
        mapper.insertAccount(id, AccountPostingWriter.accountNo(entityType, entityId, currency, type, id),
            entityType.getCode(), entityId, currency, type.getCode(),
            type.getOwnerType().getCode(), ownerId, bucket.getCode(), type.getNormalSide().getCode(),
            debitNormal ? net : BigDecimal.ZERO, debitNormal ? BigDecimal.ZERO : net, net, AccountStatus.ACTIVE.getCode());
        return id;
    }

    /** 修改账户状态。 */
    public void status(long accountId, AccountStatus status) {
        mapper.updateAccountStatus(accountId, status.getCode());
    }
}
