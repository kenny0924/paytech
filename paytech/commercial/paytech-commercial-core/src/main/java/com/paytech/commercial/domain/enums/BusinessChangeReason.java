package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * BCR 快照 _meta.reasonCode 的闭合值域：说明本次聚合写入命令的业务原因。
 * <li>只写入 snapshot_json，不是独立数据库列；同一命令的 Root 与 Child 快照使用同一原因。</li>
 */
public enum BusinessChangeReason implements CodeEnum {
    /** 创建合同 */
    AGREEMENT_CREATED("AGREEMENT_CREATED"),

    /** 合同提交签署 */
    AGREEMENT_SUBMITTED_FOR_SIGNATURE("AGREEMENT_SUBMITTED_FOR_SIGNATURE"),

    /** 登记一个 Party 的签署结果 */
    AGREEMENT_PARTY_ACCEPTED("AGREEMENT_PARTY_ACCEPTED"),

    /** 合同激活 */
    AGREEMENT_ACTIVATED("AGREEMENT_ACTIVATED"),

    /** 合同终止 */
    AGREEMENT_TERMINATED("AGREEMENT_TERMINATED"),

    /** 整体替换订阅产品 */
    AGREEMENT_PRODUCTS_REPLACED("AGREEMENT_PRODUCTS_REPLACED"),

    /** 新增开通项 */
    AGREEMENT_CAPABILITY_ADDED("AGREEMENT_CAPABILITY_ADDED"),

    /** 修改开通项 */
    AGREEMENT_CAPABILITY_UPDATED("AGREEMENT_CAPABILITY_UPDATED"),

    /** 创建定价画像 */
    PRICING_PROFILE_CREATED("PRICING_PROFILE_CREATED"),

    /** 修改 DRAFT 定价画像 */
    PRICING_PROFILE_UPDATED("PRICING_PROFILE_UPDATED"),

    /** 激活定价画像 */
    PRICING_PROFILE_ACTIVATED("PRICING_PROFILE_ACTIVATED"),

    /** 退役定价画像 */
    PRICING_PROFILE_RETIRED("PRICING_PROFILE_RETIRED");

    private final String code;

    /** 绑定稳定 Code。 */
    BusinessChangeReason(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
