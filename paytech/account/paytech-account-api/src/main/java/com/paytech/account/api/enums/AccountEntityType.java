package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Owner 账户与过账流所属的主体类型（与 ledger 账簿主体一一对应）
 * <li>{@link #getAggregatePrefix()} 是 AccountPosted 聚合 ID 的主体前缀，避免 OE 编号与 Finance Entity 主键歧义。</li>
 */
public enum AccountEntityType implements CodeEnum {
    /** CCT 经营主体，entityId 为 entityNo（如 AE001） */
    OPERATING_ENTITY("OE"),

    /** Finance 内部财务主体，entityId 为 ledger 本地主键十进制字符串 */
    FINANCE_ENTITY("FE");

    private final String aggregatePrefix;

    /** 固定聚合 ID 前缀。 */
    AccountEntityType(String aggregatePrefix) {
        this.aggregatePrefix = aggregatePrefix;
    }

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }

    /** AccountPosted 聚合 ID 的主体前缀：OE 或 FE。 */
    public String getAggregatePrefix() {
        return aggregatePrefix;
    }

    /**
     * 按聚合 ID 前缀解析主体类型
     *
     * @param aggregatePrefix OE 或 FE
     * @return 主体类型；未知前缀为空
     */
    public static AccountEntityType ofAggregatePrefix(String aggregatePrefix) {
        for (AccountEntityType type : values()) {
            if (type.aggregatePrefix.equals(aggregatePrefix)) {
                return type;
            }
        }
        return null;
    }
}
