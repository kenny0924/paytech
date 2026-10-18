package com.paytech.billing.domain;

import java.util.regex.Pattern;

import com.paytech.foundation.common.error.InvalidParameterException;

import lombok.Getter;

/**
 * 按 ledger 账簿身份（主体类型 + 主体标识 + 币种）筛选经济事实的范围
 * <li>OPERATING_ENTITY 账簿匹配事实 entity_no；FINANCE_ENTITY 账簿匹配事实 finance_entity_id。同一条合作方事实同时属于 OE 账簿与平台账簿。</li>
 */
@Getter
public class EconomicFactBookScope {
    /** OE 账簿主体类型代码（与 ledger LedgerEntityType 一致）。 */
    public static final String OPERATING_ENTITY = "OPERATING_ENTITY";

    /** Finance Entity 账簿主体类型代码。 */
    public static final String FINANCE_ENTITY = "FINANCE_ENTITY";

    private static final Pattern ENTITY_NO = Pattern.compile("[A-Z]{2}(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})");

    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    /**
     * OE 账簿的 entityNo
     * <li>FINANCE_ENTITY 账簿为空。</li>
     */
    private final String entityNo;

    /**
     * Finance Entity 账簿的主体 ID
     * <li>OPERATING_ENTITY 账簿为空。</li>
     */
    private final Long financeEntityId;

    /** 账簿币种。 */
    private final String currency;

    private EconomicFactBookScope(String entityNo, Long financeEntityId, String currency) {
        this.entityNo = entityNo;
        this.financeEntityId = financeEntityId;
        this.currency = currency;
    }

    /**
     * 解析账簿主体参数
     *
     * @param bookEntityType OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param bookEntityId entityNo 或 Finance Entity 十进制 ID
     * @param currency 账簿币种
     * @return 筛选范围
     * @throws InvalidParameterException 主体类型未知或标识格式与类型不符
     */
    public static EconomicFactBookScope of(String bookEntityType, String bookEntityId, String currency) {
        if (OPERATING_ENTITY.equals(bookEntityType) && ENTITY_NO.matcher(bookEntityId).matches()) {
            return new EconomicFactBookScope(bookEntityId, null, currency);
        }
        if (FINANCE_ENTITY.equals(bookEntityType) && DECIMAL_ID.matcher(bookEntityId).matches()) {
            return new EconomicFactBookScope(null, Long.valueOf(bookEntityId), currency);
        }
        throw new InvalidParameterException("账簿主体参数非法: " + bookEntityType + "/" + bookEntityId);
    }
}
