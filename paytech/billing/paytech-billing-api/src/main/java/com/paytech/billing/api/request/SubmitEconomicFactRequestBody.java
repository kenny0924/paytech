package com.paytech.billing.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Maker 提交一条人工或成本调整经济事实（DRAFT）。 */
@Getter
@Setter
public class SubmitEconomicFactRequestBody {
    /**
     * 经济事实类型
     * <li>MANUAL_ADJUSTMENT：影响 Owner 余额的人工调整；COST_ADJUSTMENT：渠道实际成本调整。</li>
     */
    @NotBlank
    @Pattern(regexp = "MANUAL_ADJUSTMENT|COST_ADJUSTMENT", message = "只能为 MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT")
    private String factType;

    /**
     * 来源系统代码
     * <li>例如 OPERATIONS；与 sourceReference 共同构成提交的业务唯一身份。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
    private String sourceSystem;

    /**
     * 来源引用（如调整工单号）
     * <li>同一来源系统内唯一：同来源引用同内容重复提交返回原事实，内容不同则拒绝。更正事实须使用新的来源引用。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}", message = "必须是字母或数字开头的安全 ASCII 文本，最长 128 位")
    private String sourceReference;

    /**
     * 来源事实
     * <li>COST_ADJUSTMENT 必填，指向已批准的 PAYMENT 事实；MANUAL_ADJUSTMENT 必须为空。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String parentFactId;

    /**
     * 被替代的经济事实
     * <li>可为空；非空时必须是同类型、同 Client、同 OE、同币种的 APPROVED 事实，批准时原子置为 SUPERSEDED。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String supersedesFactId;

    /** CCT Client 稳定业务 ID */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String clientId;

    /** Operating Entity 编号（两位国家码 + 001—999） */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})", message = "必须是 entityNo 格式")
    private String entityNo;

    /**
     * CCT Local Partner
     * <li>可为空；与 partnerAgreementOeId、financeEntityId 全有或全无。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String localPartnerId;

    /** Partner Agreement 与 OE 的关联 ID，可空条件同 localPartnerId */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String partnerAgreementOeId;

    /** 平台服务费收取方 Finance Entity（ledger 主键），可空条件同 localPartnerId */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String financeEntityId;

    /**
     * 全部金额的币种
     * <li>必须在 billing 币种规则只读副本中为 ACTIVE；金额精度不得超过该币种 minor unit。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * Finance 渠道清算账户（reconciliation t_channel_account.id）
     * <li>COST_ADJUSTMENT 必填；MANUAL_ADJUSTMENT 可为空。本期由调用方给出，billing 不向 reconciliation 核对。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String channelAccountId;

    /**
     * 编制人平台用户标识
     * <li>未接统一身份，由内部调用方传入；批准人必须不同。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String makerId;

    /**
     * 全部明细行
     * <li>1 至 100 行，按提交顺序编号 lineNo = 1..n。</li>
     */
    @NotNull
    @Size(min = 1, max = 100)
    private List<@Valid @NotNull EconomicFactLineRequestBody> lines;
}
