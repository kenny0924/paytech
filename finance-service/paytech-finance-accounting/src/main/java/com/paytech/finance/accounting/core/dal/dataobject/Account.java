package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.AccountStatus;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.NormalSide;
import com.paytech.finance.accounting.core.enums.OwnerType;
import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_account：在具体账簿和可过账科目下按核算对象与余额桶建立唯一记账账户，并保存可由不可变 Journal 重建的当前余额投影；CLIENT_PAYABLE 与 PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner PENDING 是共享待结算桶；Partner reversal 无法由 PENDING/AVAILABLE/LOCKED liability 吸收时使用资产科目 PARTNER_RECOVERY_RECEIVABLE/NONE；Client Recovery 使用 CLIENT_RECOVERY_RECEIVABLE/NONE，保证金使用 CLIENT_SECURITY_DEPOSIT/NONE；Channel 使用 CHANNEL_UNSETTLED_RECEIVABLE、CHANNEL_SETTLEMENT_RECEIVABLE、CHANNEL_UNSETTLED_PAYABLE、CHANNEL_SETTLEMENT_PAYABLE 四个科目表达交易形成、交易对账完整和银行确认三个阶段，Channel Cost 单独使用 CHANNEL_FEE_ACCRUAL，银行现金使用 BANK_CASH。 */
@Getter
@Setter
public class Account {
    /**
     * 服务侧生成的统一记账账户 Snowflake 主键
     */
    private Long id;

    /**
     * 该账户严格归属的本库经营主体币种账簿
     */
    private Long ledgerBookId;

    /**
     * 该账户使用且必须允许过账的本库会计科目定义
     */
    private Long chartAccountId;

    /**
     * 决定账户余额按经营主体、内部财务主体、Client、Local Partner、渠道账户、银行账户或税务机关隔离的类型
     * <li>允许值：OPERATING_ENTITY、FINANCE_ENTITY、CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY。</li>
     */
    private OwnerType ownerType;

    /**
     * 按owner_type解释的稳定字符串；OE为entityNo，其余数字身份保存规范十进制字符串；本库对象使用本地主键，外部对象不建立跨库外键
     */
    private String ownerId;

    /**
     * PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner 的 PENDING 是共享待结算桶；PARTNER_RECOVERY_RECEIVABLE 使用 NONE；CLIENT_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，CLIENT_RECOVERY_RECEIVABLE/NONE、CLIENT_SECURITY_DEPOSIT/NONE 以及 Channel 三阶段账户均使用独立会计科目并采用 NONE，不复用应付款余额桶
     * <li>允许值：PENDING、AVAILABLE、LOCKED、NONE。</li>
     */
    private BalanceBucket balanceBucket;

    /**
     * 创建账户时从会计科目复制并冻结的正常余额方向；账户仍允许借贷双向过账
     * <li>允许值：DEBIT、CREDIT。</li>
     */
    private NormalSide normalSide;

    /**
     * 该具体核算账户当前是否允许新的会计分录过账
     * <li>允许值：ACTIVE、INACTIVE、CLOSED。</li>
     */
    private AccountStatus accountStatus;

    /**
     * 截至末次分录序号已计入该账户的累计借方金额
     */
    private BigDecimal debitTotal;

    /**
     * 截至末次分录序号已计入该账户的累计贷方金额
     */
    private BigDecimal creditTotal;

    /**
     * 按照本账户正常余额方向计算且可由不可变分录重建的有符号余额；正数表示正常方向，负数表示反向余额
     */
    private BigDecimal netBalance;

    /**
     * 已经应用到账户余额投影的本账簿最后分录序号
     */
    private Long lastJournalSequence;

    /**
     * 仅用于账户余额投影顺序条件更新的乐观锁版本，不表示账户身份版本
     */
    private Long balanceVersion;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
