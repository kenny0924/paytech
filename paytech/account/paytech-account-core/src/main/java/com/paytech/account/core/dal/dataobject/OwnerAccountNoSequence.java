package com.paytech.account.core.dal.dataobject;

import java.time.Instant;

import com.paytech.account.api.enums.AccountEntityType;

import lombok.Getter;
import lombok.Setter;

/** t_owner_account_no_sequence：按主体 + 币种 + 科目代码为 Owner 账户分配可读账户号序号，只在开户事务内原子递增。 */
@Getter
@Setter
public class OwnerAccountNoSequence {
    /** 账户服务生成的 Snowflake 主键；序列行已存在时不使用。 */
    private Long id;

    /** 账户所属法律主体类型。 */
    private AccountEntityType entityType;

    /** 账户所属主体标识：OE 为 entityNo，Finance Entity 为十进制主键。 */
    private String entityId;

    /** 大写 ISO 4217 币种。 */
    private String currency;

    /** Owner 账户类型目录中的科目代码。 */
    private String accountCode;

    /** 最近一次分配的序号；已分配序号不复用，允许空洞。 */
    private Long lastValue;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
