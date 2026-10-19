package com.paytech.ledger.api.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 财务主体列表项。 */
@Getter
@Setter
public class FinanceEntityResponseBody {
    /** 财务主体主键。 */
    private String financeEntityId;

    /** 财务主体代码，全局唯一。 */
    private String entityCode;

    /** 对应的 CCT 法律 Party 主键，全局唯一。 */
    private String legalPartyId;

    /** 展示名称。 */
    private String displayName;

    /**
     * 主体状态
     * <li>ACTIVE 或 CLOSED。</li>
     */
    private String status;

    /** 创建时间（UTC）。 */
    private Instant createTime;
}
