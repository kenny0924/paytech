package com.paytech.reconciliation.api.cash.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 平台银行账户；只含账号末四位。 */
@Getter
@Setter
public class CashAccountResponseBody {
    /** 银行账户主键 */
    private String cashAccountId;

    /** 主体类型：OPERATING_ENTITY、FINANCE_ENTITY */
    private String entityType;

    /** 主体标识 */
    private String entityId;

    /** 资金用途：CHANNEL_SETTLEMENT、PAYOUT、TAX */
    private String accountPurpose;

    /** 开户银行代码 */
    private String bankCode;

    /** 账户显示名称 */
    private String accountName;

    /** 账户币种 */
    private String currency;

    /** 银行账号末四位 */
    private String accountLast4;

    /** 状态：PENDING_VERIFICATION、ACTIVE、SUSPENDED、CLOSED */
    private String status;

    /**
     * 待复核的变更
     * <li>CLOSE 表示有待复核的关闭申请；为空表示没有（新建待复核由 status 表示）。</li>
     */
    private String pendingAction;

    /**
     * 最近一次提交复核内容的摘要
     * <li>复核时须原样确认。</li>
     */
    private String contentHash;

    /** 最近一次提交人 Account ID */
    private String makerId;

    /**
     * 最近一次复核人 Account ID
     * <li>未复核为空。</li>
     */
    private String checkerId;

    /** 乐观锁版本 */
    private String version;

    /** 创建的 UTC 时间 */
    private Instant createTime;

    /** 最后更新的 UTC 时间 */
    private Instant updateTime;
}
