package com.paytech.account.domain;

import com.paytech.account.core.enums.BookEntityType;

/**
 * ledger 账簿的主体身份：主体类型、主体标识与币种（与 ledger 账簿唯一键及 billing 账簿主体参数一致）
 *
 * @param entityType 主体类型
 * @param entityId OE 为 entityNo，Finance Entity 为 ledger 本地主键十进制字符串
 * @param currency 大写 ISO 4217 币种
 */
public record BookIdentity(BookEntityType entityType, String entityId, String currency) {

    /** 经营主体账簿身份。 */
    public static BookIdentity operatingEntity(String entityNo, String currency) {
        return new BookIdentity(BookEntityType.OPERATING_ENTITY, entityNo, currency);
    }

    /** 日志与错误上下文文本。 */
    @Override
    public String toString() {
        return entityType.getCode() + "/" + entityId + "/" + currency;
    }
}
