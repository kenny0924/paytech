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
    PRICING_PROFILE_RETIRED("PRICING_PROFILE_RETIRED"),

    /** 创建商业条款方案 */
    TERM_PLAN_CREATED("TERM_PLAN_CREATED"),

    /** 创建 DRAFT 条款版本 */
    TERM_VERSION_CREATED("TERM_VERSION_CREATED"),

    /** 整体替换 DRAFT 版本价格集合 */
    TERM_VERSION_PRICES_REPLACED("TERM_VERSION_PRICES_REPLACED"),

    /** 条款版本提交审批（快照记录 Maker） */
    TERM_VERSION_SUBMITTED("TERM_VERSION_SUBMITTED"),

    /** 条款版本审批通过（快照记录 Maker 与 Checker） */
    TERM_VERSION_APPROVED("TERM_VERSION_APPROVED"),

    /** 条款版本驳回（快照记录 Maker 与 Checker） */
    TERM_VERSION_REJECTED("TERM_VERSION_REJECTED"),

    /** 条款版本排期；同命令关闭前一 ACTIVE 版本的生效窗口 */
    TERM_VERSION_SCHEDULED("TERM_VERSION_SCHEDULED"),

    /** 条款版本激活；同命令替代前一 ACTIVE 版本，方案首次生效转 ACTIVE */
    TERM_VERSION_ACTIVATED("TERM_VERSION_ACTIVATED");

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
