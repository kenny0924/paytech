package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 商业关系快照中的角色（relationshipSnapshot.bindings[].role）
 * <li>全部 11 个可取值；本轮只产出 CLIENT、SELLER、ACQUIRING_OE、OE_PROVIDER、FUNDS_CUSTODIAN、TAX_RESPONSIBLE、PLATFORM_FEE_PAYER、
 * ACQUIRER_SUB_MERCHANT（仅 PSP）与 PLATFORM。</li>
 * <li>bindings 按本枚举声明顺序固定排列，同一角色至多一行。</li>
 */
public enum RelationshipRole implements CodeEnum {
    /** 平台 Client */
    CLIENT("CLIENT"),

    /** 面向消费者的卖方：MOR 为收单 OE，PSP 为 Client */
    SELLER("SELLER"),

    /** 收单 Operating Entity */
    ACQUIRING_OE("ACQUIRING_OE"),

    /** 收单 OE 的提供方：PARTNER 来源关系上的 Local Partner */
    OE_PROVIDER("OE_PROVIDER"),

    /** 资金保管方：资金经平台结算时为收单 OE */
    FUNDS_CUSTODIAN("FUNDS_CUSTODIAN"),

    /** 税务责任方：MOR 为收单 OE，PSP 为 Client */
    TAX_RESPONSIBLE("TAX_RESPONSIBLE"),

    /** 平台服务费承担方：OE 提供方 */
    PLATFORM_FEE_PAYER("PLATFORM_FEE_PAYER"),

    /** 收单行视角的次级商户：仅 PSP，为 Client */
    ACQUIRER_SUB_MERCHANT("ACQUIRER_SUB_MERCHANT"),

    /** 推荐方；本轮不产出 */
    REFERRER("REFERRER"),

    /** 分账收款方；本轮不产出 */
    SPLIT_PAYEE("SPLIT_PAYEE"),

    /** 技术服务商：平台自身，法律主体为 Partner 合同的 PLATFORM_SIGNATORY Party */
    PLATFORM("PLATFORM");

    private final String code;

    RelationshipRole(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
