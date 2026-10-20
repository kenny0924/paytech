package com.paytech.account.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.AccountStatus;

import lombok.Getter;
import lombok.Setter;

/** t_owner_account：明细账 Owner 账户主数据与实时余额，余额只由本库流水维护并以 balance_version CAS 保护并发写入。 */
@Getter
@Setter
public class OwnerAccount {
    /** 账户服务生成的 Owner 账户 Snowflake 主键。 */
    private Long id;

    /**
     * 财务人员可读账户号
     * <li>{主体段}-{币种}-{科目编号}-S{序号}，开户时生成后不变；只用于显示与搜索，不进入事件载荷、不作为关联键。</li>
     */
    private String accountNo;

    /** 账户所属法律主体类型。 */
    private AccountEntityType entityType;

    /** 账户所属主体标识：OE 为 entityNo，Finance Entity 为十进制主键。 */
    private String entityId;

    /** 大写 ISO 4217 币种。 */
    private String currency;

    /**
     * Owner 账户类型代码
     * <li>取值见 account-api OwnerAccountType，开放目录以 String 承载。</li>
     */
    private String accountCode;

    /** 核算对象类型。 */
    private OwnerType ownerType;

    /** 核算对象规范十进制标识。 */
    private String ownerId;

    /** 余额桶。 */
    private BalanceBucket balanceBucket;

    /** 正常余额方向，开户时由账户类型目录冻结。 */
    private EntrySide normalSide;

    /** 累计借方金额。 */
    private BigDecimal debitTotal;

    /** 累计贷方金额。 */
    private BigDecimal creditTotal;

    /** 按正常方向计算、允许为负的净余额。 */
    private BigDecimal netBalance;

    /** 余额乐观锁版本，每次写入加一。 */
    private Long balanceVersion;

    /** 账户状态。 */
    private AccountStatus accountStatus;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
