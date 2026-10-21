package com.paytech.reconciliation.core.dal.dataobject;

import java.time.Instant;

import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.CashAccountPendingAction;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;

import lombok.Getter;
import lombok.Setter;

/**
 * t_cash_account（银行账户）
 * <li>平台银行账户：账号经 Vault 保护，含资金用途；新建与关闭都经复核。换用途即关闭旧户、开新户。</li>
 */
@Getter
@Setter
public class CashAccount {
    /**
     * 银行账户ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 主体类型
     * <li>账户所属主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。</li>
     */
    private ReconEntityType entityType;

    /**
     * 主体标识
     * <li>按主体类型解释：经营主体为 entityNo，财务主体为 ledger 主键十进制字符串；不建跨库外键。</li>
     */
    private String entityId;

    /**
     * 资金用途
     * <li>资金用途；同主体 + 币种 + 用途只允许一个 ACTIVE；创建后不可改。允许值：CHANNEL_SETTLEMENT、PAYOUT、TAX。</li>
     */
    private AccountPurpose accountPurpose;

    /**
     * 银行代码
     * <li>开户银行代码。取值来源：运营录入的银行机构代码；非固定枚举。</li>
     */
    private String bankCode;

    /**
     * 账户名称
     * <li>账户显示名称。</li>
     */
    private String accountName;

    /**
     * 币种
     * <li>账户币种。取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 账号Vault令牌
     * <li>银行账号的 Vault 令牌（数据类型 CASH_ACCOUNT_NUMBER_V1，REFERENCE 模式）；本库不存明文。</li>
     */
    private String accountVaultToken;

    /**
     * 账号末四位
     * <li>银行账号末四位，用于展示与核对对账单。</li>
     */
    private String accountLast4;

    /**
     * 状态
     * <li>账户状态；新建待复核为 PENDING_VERIFICATION。允许值：PENDING_VERIFICATION、ACTIVE、SUSPENDED、CLOSED。</li>
     */
    private CashAccountStatus status;

    /**
     * 待复核变更
     * <li>待复核的变更；为空表示没有待复核的关闭申请（新建待复核由 status 表示）。允许值：CLOSE。</li>
     */
    private CashAccountPendingAction pendingAction;

    /**
     * 内容Hash
     * <li>最近一次提交复核内容的 SHA-256，复核人确认的必须与之相同。</li>
     */
    private String contentHash;

    /**
     * 提交人ID
     * <li>最近一次提交（新建或关闭）的 Platform 账户 ID。</li>
     */
    private Long makerId;

    /**
     * 复核人ID
     * <li>最近一次复核人 Platform 账户 ID，必须与 maker_id 不同；未复核为空。</li>
     */
    private Long checkerId;

    /**
     * 乐观锁版本
     * <li>状态与复核更新的乐观锁版本。</li>
     */
    private Long version;

    /**
     * 创建时间
     * <li>创建的 UTC 微秒时间。</li>
     */
    private Instant createTime;

    /**
     * 更新时间
     * <li>最后更新的 UTC 微秒时间。</li>
     */
    private Instant updateTime;
}
