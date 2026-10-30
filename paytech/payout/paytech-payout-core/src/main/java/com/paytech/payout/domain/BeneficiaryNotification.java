package com.paytech.payout.domain;

import java.util.Map;

import com.paytech.payout.api.enums.BeneficiaryOwnerType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次收款账户状态通知：模板、Owner 租户收件人判定与模板参数
 * <li>参数只有银行名、末四位、币种、可用时间与原因代码，不含原值或 Token。</li>
 */
@Getter
@AllArgsConstructor
public class BeneficiaryNotification {
    /** Platform 去重键 BENEFICIARY:{accountId}:{eventNo} */
    private final String sourceEventKey;

    /** 邮件模板编码 */
    private final String templateCode;

    /** Owner 类型 */
    private final BeneficiaryOwnerType ownerType;

    /** Owner 标识 */
    private final Long ownerId;

    /** 收件人判定权限 */
    private final String permissionCode;

    /** 默认语言 */
    private final String defaultLocale;

    /** 模板参数 */
    private final Map<String, String> parameters;
}
