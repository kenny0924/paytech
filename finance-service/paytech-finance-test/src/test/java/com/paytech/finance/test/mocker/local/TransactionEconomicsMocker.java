package com.paytech.finance.test.mocker.local;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.finance.accounting.core.enums.FinanceEntityStatus;
import com.paytech.finance.accounting.core.enums.ReferenceDataStatus;
import com.paytech.finance.test.builder.EconomicComponentBuilder;
import com.paytech.finance.test.builder.TransactionEconomicsBuilder;
import com.paytech.finance.test.dal.AccountingTestMapper;
import com.paytech.finance.test.dal.row.ChannelAccountRow;
import com.paytech.finance.test.dal.row.EconomicComponentRow;
import com.paytech.finance.test.dal.row.TransactionEconomicsRow;
import com.paytech.foundation.id.IdGenerator;

/**
 * 交易自动记账前置事实：直接插入交易经济结果及组件、渠道清算账户和平台 Finance Entity 账簿
 * <li>本期没有交易经济创建入口，夹具代表已冻结的计算结果；不调用被测引擎。</li>
 */
@TestComponent
public class TransactionEconomicsMocker {
    /** 渠道清算账户夹具使用的 Payment 渠道标识。 */
    public static final long CHANNEL_ID = 610001L;

    /** 渠道清算账户启用状态。 */
    public static final String CHANNEL_ACCOUNT_ACTIVE = ReferenceDataStatus.ACTIVE.getCode();

    private final IdGenerator idGenerator;
    private final AccountingTestMapper testMapper;
    private final LedgerMocker ledgerMocker;

    public TransactionEconomicsMocker(IdGenerator idGenerator, AccountingTestMapper testMapper,
        LedgerMocker ledgerMocker) {
        this.idGenerator = idGenerator;
        this.testMapper = testMapper;
        this.ledgerMocker = ledgerMocker;
    }

    /** 插入经济结果及其组件（组件序号按入参顺序从 1 开始），返回带主键的经济结果行。 */
    public TransactionEconomicsRow mockEconomics(TransactionEconomicsBuilder builder,
        EconomicComponentBuilder... components) {
        TransactionEconomicsRow economics = builder.build();
        economics.setId(idGenerator.nextId());
        testMapper.insertTransactionEconomics(economics);
        int componentNo = 1;
        for (EconomicComponentBuilder component : components) {
            EconomicComponentRow row = component.build();
            row.setId(idGenerator.nextId());
            row.setTransactionEconomicsId(economics.getId());
            row.setComponentNo(componentNo++);
            row.setCreateTime(economics.getCreateTime());
            testMapper.insertEconomicComponent(row);
        }
        return economics;
    }

    /** 插入 ACTIVE 渠道清算账户（按 MID + 币种唯一），返回主键。 */
    public long mockChannelAccount(long midId, String entityNo, String currency) {
        ChannelAccountRow row = new ChannelAccountRow();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        row.setId(idGenerator.nextId());
        row.setChannelId(CHANNEL_ID);
        row.setMidId(midId);
        row.setEntityNo(entityNo);
        row.setCurrency(currency);
        row.setStatus(CHANNEL_ACCOUNT_ACTIVE);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        testMapper.insertChannelAccount(row);
        return row.getId();
    }

    /** 插入 ACTIVE 平台 Finance Entity 及其币种账簿、日历和当月 OPEN 期间。 */
    public LedgerFixture mockPlatformFinanceEntityLedger(String entityCode, long legalPartyId, String currency) {
        long financeEntityId = ledgerMocker.mockFinanceEntity(entityCode, legalPartyId, FinanceEntityStatus.ACTIVE);
        return ledgerMocker.mockFinanceEntityLedger(financeEntityId, currency);
    }

    /** 只插入 ACTIVE 平台 Finance Entity，不建账簿，用于账簿缺失场景。 */
    public long mockPlatformFinanceEntity(String entityCode, long legalPartyId) {
        return ledgerMocker.mockFinanceEntity(entityCode, legalPartyId, FinanceEntityStatus.ACTIVE);
    }
}
