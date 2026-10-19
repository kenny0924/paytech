package com.paytech.account.api.response;

import lombok.Getter;
import lombok.Setter;

/** 余额桶移动结果。 */
@Getter
@Setter
public class BalanceMovementResponseBody {
    /** 移动记录主键（t_balance_movement.id），十进制字符串。 */
    private String movementId;

    /** 本次移动生成的过账批次主键，十进制字符串。 */
    private String postingId;

    /** 该批次在所属过账流内的序号，十进制字符串。 */
    private String postingSequence;

    /**
     * 是否为同来源重放
     * <li>true 时 HTTP 200，返回首次执行的结果；false 时 HTTP 201。</li>
     */
    private boolean replayed;
}
