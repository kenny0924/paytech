package com.paytech.channel.connector.domain;

import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** MID 列表查询条件；全部筛选可空，空表示不限，分页参数已校验。 */
@Getter
@AllArgsConstructor
public class ChannelMidListCriteria {
    /** 所属 Channel 主键，可空。 */
    private final Long channelId;

    /** 当前绑定技术账号主键，可空。 */
    private final Long channelAccountId;

    /** Operating Entity 编号，可空。 */
    private final String entityNo;

    /** MID 状态，可空。 */
    private final ChannelStatus status;

    /**
     * 外部 MID 前缀，可空
     * <li>CC-B 补充：前缀匹配，下划线等通配符按文字处理。</li>
     */
    private final String externalMidCode;

    /** 已校验的分页参数。 */
    private final PagingRequestBody paging;

    /** 外部 MID 的 LIKE 字面量前缀：转义反斜杠、百分号与下划线；未筛选时为空。 */
    public String getExternalMidCodePrefix() {
        if (externalMidCode == null) {
            return null;
        }
        return externalMidCode.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
