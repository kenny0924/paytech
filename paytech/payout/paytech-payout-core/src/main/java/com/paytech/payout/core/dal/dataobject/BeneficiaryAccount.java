package com.paytech.payout.core.dal.dataobject;

import java.time.Instant;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;

import lombok.Getter;
import lombok.Setter;

/**
 * t_beneficiary_account（收款账户）
 * <li>Owner 的收款账户：原值只在 Vault，库内只有 Token、脱敏户名、末四位与状态；提交即冻结，冻结字段变化必须新建账户。</li>
 * <li>生成列 active_key 只服务唯一索引 uk_beneficiary_owner_token，不映射、不写入。</li>
 */
@Getter
@Setter
public class BeneficiaryAccount {
    /**
     * 收款账户ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * Owner类型
     * <li>CLIENT、LOCAL_PARTNER。</li>
     */
    private BeneficiaryOwnerType ownerType;

    /**
     * Owner标识
     * <li>client-master 的 Client 或 Local Partner ID；不建跨库关系。</li>
     */
    private Long ownerId;

    /**
     * 账户编号
     * <li>Owner 内可读编号 BA- 加 4 位以上递增序号。</li>
     */
    private String accountCode;

    /**
     * 收款方式
     * <li>LOCAL、SWIFT。</li>
     */
    private BeneficiaryRailType railType;

    /**
     * 开户国家
     * <li>ISO 3166-1 alpha-2；非固定枚举。</li>
     */
    private String bankCountry;

    /**
     * 币种
     * <li>ISO 4217；非固定枚举。</li>
     */
    private String currency;

    /**
     * 银行名称
     * <li>非敏感显示名称。</li>
     */
    private String bankName;

    /**
     * 银行代码
     * <li>本地银行代码或 SWIFT BIC；只用 IBAN 的国家为空。</li>
     */
    private String bankCode;

    /**
     * 账户原值Vault令牌
     * <li>PAYOUT_BENEFICIARY_V1 REFERENCE Token；不得进入日志、事件或响应。</li>
     */
    private String beneficiaryVaultToken;

    /**
     * 账号查重令牌
     * <li>PAYOUT_BANK_ACCOUNT_KEY_V1 STABLE Token（规范化账号）；不得进入日志、事件或响应。</li>
     */
    private String bankAccountToken;

    /**
     * 户名脱敏值
     */
    private String holderNameMasked;

    /**
     * 账号末四位
     */
    private String accountLast4;

    /**
     * 内容Hash
     * <li>提交时冻结字段（不含原值）的 SHA-256；Maker / Checker 审阅的摘要必须与之一致。</li>
     */
    private String contentHash;

    /**
     * 提交人类型
     * <li>CLIENT_MEMBER、OPERATIONS。</li>
     */
    private BeneficiaryActorType submittedByType;

    /**
     * 提交人标识
     */
    private Long submittedBy;

    /**
     * 核验意见人标识
     * <li>尚无 Maker 意见时为空。</li>
     */
    private Long reviewMakerId;

    /**
     * 核验意见
     * <li>APPROVE、REJECT；尚无意见时为空。</li>
     */
    private MakerReviewDecision reviewMakerDecision;

    /**
     * 复核人标识
     * <li>尚未复核时为空。</li>
     */
    private Long checkerId;

    /**
     * 复核决定
     * <li>APPROVED、REJECTED；尚未复核时为空。</li>
     */
    private CheckerDecision checkerDecision;

    /**
     * 复核时间
     * <li>UTC 微秒；尚未复核时为空。</li>
     */
    private Instant decisionTime;

    /**
     * 原因代码
     * <li>最近一次拒绝、停用或关闭的原因；开放目录，用字符串承载。</li>
     */
    private String reasonCode;

    /**
     * 给 Owner 的说明
     * <li>运营在拒绝时填写的可选文字（≤500），随拒绝通知的 reasonText 发送；停用、关闭、恢复时清空。</li>
     */
    private String reasonNote;

    /**
     * 核验意见备注
     * <li>Maker 出具意见时的可选内部备注（≤500），只给运营视图；恢复重新核验时清空。</li>
     */
    private String reviewMakerNote;

    /**
     * 冷却结束时间
     * <li>VERIFIED 时 = decisionTime + 冷却时长；其他状态为空。</li>
     */
    private Instant coolingUntil;

    /**
     * 是否默认
     * <li>同 Owner、同币种至多一个，由 Owner 锁内的应用逻辑保证。</li>
     */
    private Boolean isDefault;

    /**
     * 状态
     * <li>PENDING_REVIEW、VERIFIED、REJECTED、SUSPENDED、CLOSED。</li>
     */
    private BeneficiaryAccountStatus status;

    /**
     * 乐观锁版本
     */
    private Long version;

    /**
     * 创建时间
     */
    private Instant createTime;

    /**
     * 更新时间
     */
    private Instant updateTime;

    /**
     * 当前是否可用于出款：VERIFIED 且冷却已结束（B4，按 coolingUntil 实时判定）
     *
     * @param now 判定时刻
     * @return 可用为 true
     */
    public boolean usableAt(Instant now) {
        return status == BeneficiaryAccountStatus.VERIFIED && coolingUntil != null && !now.isBefore(coolingUntil);
    }

    /**
     * 是否处于给定状态之一
     *
     * @param statuses 候选状态
     * @return 命中为 true
     */
    public boolean statusIn(BeneficiaryAccountStatus... statuses) {
        for (BeneficiaryAccountStatus candidate : statuses) {
            if (status == candidate) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否属于给定 Owner
     *
     * @param type Owner 类型
     * @param id Owner 标识
     * @return 相同为 true
     */
    public boolean ownedBy(BeneficiaryOwnerType type, Long id) {
        return ownerType == type && ownerId.equals(id);
    }
}
