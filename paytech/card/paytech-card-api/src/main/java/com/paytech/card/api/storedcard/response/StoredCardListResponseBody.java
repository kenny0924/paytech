package com.paytech.card.api.storedcard.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 已存卡列表响应 */
@Getter
@Setter
public class StoredCardListResponseBody {
    /** 有效且未过期的已存卡，按创建先后排列 */
    private List<StoredCard> storedCards;

    /** 对外可展示的已存卡 */
    @Getter
    @Setter
    public static class StoredCard {
        /** 对外已存卡 Token */
        private String storedCardToken;

        /** 掩码卡号：前 6 位、6 个星号与后 4 位 */
        private String maskedPan;

        /** 卡号后 4 位 */
        private String last4;

        /**
         * 卡组织（主品牌），{@code CardBrand} Code，供展示图标
         * <li>按当前生效 BIN 库实时查询，只用于展示；回切 BIN 库后可能与历史交易快照不同。未识别或没有生效版本时为 null。</li>
         */
        private String cardBrand;

        /** 有效期月份，两位 */
        private String expiryMonth;

        /** 有效期年份，四位 */
        private String expiryYear;

        /** 保存时间，ISO-8601 UTC */
        private String createdAt;
    }
}
