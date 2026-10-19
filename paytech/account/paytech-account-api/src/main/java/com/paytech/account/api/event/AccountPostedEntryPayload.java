package com.paytech.account.api.event;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * AccountPosted 载荷中的一条明细流水（t_account_entry），按行号升序
 * <li>JSON 中标量全为字符串（ID、行号、金额为十进制，金额按币种精度补齐），缺省为 JSON null；Jackson 默认绑定到本类字段类型。</li>
 * <li>ledger 每条流水生成一行控制科目分录行：科目 = accountCode，方向与金额照抄，account_entry_id = entryId。</li>
 */
@Getter
@Setter
public class AccountPostedEntryPayload {
    /** 流水主键（t_account_entry.id）。 */
    private Long entryId;

    /** 批次内行号，从 1 开始。 */
    private Integer lineNo;

    /** Owner 账户主键（t_owner_account.id）。 */
    private Long accountId;

    /** 科目代码，取值见 {@code OwnerAccountType}。 */
    private String accountCode;

    /** 核算对象类型，取值见 {@code OwnerType}。 */
    private String ownerType;

    /** 核算对象规范十进制标识。 */
    private String ownerId;

    /** 余额桶，取值见 {@code BalanceBucket}。 */
    private String balanceBucket;

    /** 借贷方向，取值见 {@code EntrySide}。 */
    private String entrySide;

    /** 正金额，主货币单位，按批次币种精度补齐。 */
    private BigDecimal amount;

    /**
     * 来源计费事实明细行号
     * <li>事实批次的普通行与冲回行为对应事实行号；桶移动批次为空。</li>
     */
    private Integer factLineNo;

    /**
     * 被冲回的原流水主键
     * <li>替代事实冲回被替代事实影响时非空，冲回行方向已与原流水相反；其余为空。</li>
     */
    private Long reversesEntryId;
}
