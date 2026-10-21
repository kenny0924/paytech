package com.paytech.reconciliation.api.clearing.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 已激活但尚未归入清算账户的 MID + 币种列表。 */
@Getter
@Setter
public class UnassignedMidListResponseBody {
    /** 未归属项，按渠道编码、经营主体、MID 主键、币种升序 */
    private List<UnassignedMid> items;

    /** 一个未归属的 MID + 币种。 */
    @Getter
    @Setter
    public static class UnassignedMid {
        /** channel-connector MID 主键 */
        private String midId;

        /** 渠道侧外部 MID 编码 */
        private String externalMidCode;

        /** 渠道编码 */
        private String channelCode;

        /** 经营主体编号 */
        private String entityNo;

        /** 能力组中的币种 */
        private String currency;
    }
}
