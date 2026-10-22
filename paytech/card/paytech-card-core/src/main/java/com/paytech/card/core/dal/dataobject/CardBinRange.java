package com.paytech.card.core.dal.dataobject;

import java.time.Instant;

import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;

import lombok.Getter;
import lombok.Setter;

/** t_card_bin_range 的完整持久化映射：一个 BIN 前缀与标准化卡属性，不含完整卡号。 */
@Getter
@Setter
public class CardBinRange {
    /** 号段标识。 */
    private Long rangeId;

    /** 所属 BIN 库版本。 */
    private Long versionId;

    /** 号段前缀，6～8 位数字。 */
    private String binPrefix;

    /** 前缀位数，等于 binPrefix 长度。 */
    private Integer prefixLength;

    /** 卡组织（主品牌）。 */
    private CardBrand cardBrand;

    /** 借贷记类型；未提供时为 null。 */
    private CardFundingType fundingType;

    /** 卡等级，开放代码；未提供时为 null。 */
    private String cardLevel;

    /** 卡产品类别；未提供时为 null。 */
    private CardProduct cardProduct;

    /** 发卡国 ISO 3166-1 alpha-2。 */
    private String issuerCountry;

    /** 发卡区域，导入时由 Platform 国家区域映射推导并冻结。 */
    private String issuerRegion;

    /** 发卡行名称；未提供时为 null。 */
    private String issuerName;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间，等于创建时间。 */
    private Instant updateTime;
}
