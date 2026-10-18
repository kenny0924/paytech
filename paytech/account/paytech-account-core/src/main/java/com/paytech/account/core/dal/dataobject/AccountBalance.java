package com.paytech.account.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.BalanceStatus;
import com.paytech.account.core.enums.BookEntityType;
import com.paytech.account.core.enums.NormalSide;

import lombok.Getter;
import lombok.Setter;

/** t_account_balance：Owner 余额桶的实时权威（design/04 §3.2），只由经济事实消费与重建维护，余额版本 CAS 保护并发写入。 */
@Getter
@Setter
public class AccountBalance {
    /** 账户服务生成的余额账户 Snowflake 主键，不复用 ledger 账户 ID。 */
    private Long id;

    /** 余额所属 ledger 账簿的主体类型。 */
    private BookEntityType bookEntityType;

    /** 余额所属 ledger 账簿的主体标识：OE 为 entityNo，Finance Entity 为 ledger 本地主键十进制字符串。 */
    private String bookEntityId;

    /** 余额所属账簿的大写 ISO 4217 币种。 */
    private String currency;

    /** 余额对应的 ledger 标准科目代码。 */
    private String accountCode;

    /** 余额隔离的核算对象类型。 */
    private OwnerType ownerType;

    /** 按 ownerType 解释的规范十进制稳定标识。 */
    private String ownerId;

    /** 应付款余额桶；独立科目使用 NONE。 */
    private BalanceBucket balanceBucket;

    /** 科目正常余额方向，用于计算净余额。 */
    private NormalSide normalSide;

    /** 已应用经济事实累计的借方金额。 */
    private BigDecimal debitTotal;

    /** 已应用经济事实累计的贷方金额。 */
    private BigDecimal creditTotal;

    /** 按正常方向计算、允许为负的当前净余额。 */
    private BigDecimal netBalance;

    /** 余额条件更新使用的乐观锁版本。 */
    private Long balanceVersion;

    /** 最近一次改变本余额的经济事实 ID；尚未变化时为空。 */
    private Long lastFactId;

    /** 最近一次改变本余额的经济事实版本；尚未变化时为空。 */
    private Long lastFactVersion;

    /** 余额账户状态。 */
    private BalanceStatus status;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
