package com.paytech.account.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.MovementStatus;

import lombok.Getter;
import lombok.Setter;

/** t_balance_movement：余额桶移动命令记录，按 (source_type, source_ref) 幂等，借贷写入 t_account_entry。 */
@Getter
@Setter
public class BalanceMovement {
    /** 账户服务生成的 Snowflake 主键。 */
    private Long id;

    /** 账户所属主体类型。 */
    private AccountEntityType entityType;

    /** 账户所属主体标识。 */
    private String entityId;

    /** 移动金额币种。 */
    private String currency;

    /** 移动所在的 Owner 账户类型代码。 */
    private String accountCode;

    /** 核算对象类型。 */
    private OwnerType ownerType;

    /** 核算对象规范十进制标识。 */
    private String ownerId;

    /** 转出余额桶。 */
    private BalanceBucket fromBucket;

    /** 转入余额桶。 */
    private BalanceBucket toBucket;

    /** 已按币种精度归整的正移动金额。 */
    private BigDecimal amount;

    /** 发起移动的来源类型。 */
    private MovementSourceType sourceType;

    /** 来源业务的稳定引用。 */
    private String sourceRef;

    /** 本次移动生成的入账批次。 */
    private Long postingId;

    /** 经命令接口发起时的操作人；内部任务为空。 */
    private Long operatorId;

    /** 移动命令状态。 */
    private MovementStatus movementStatus;

    /** 记录创建的 UTC 微秒时间。 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
