package com.paytech.reconciliation.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询未归属 MID + 币种的条件，均可为空。 */
@Getter
@AllArgsConstructor
public class ListUnassignedMidsRequest {
    /** 经营主体编号 */
    private final String entityNo;

    /** 渠道编码 */
    private final String channelCode;

    /** 币种 */
    private final String currency;
}
