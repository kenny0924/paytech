package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 命中所在的一种名单类型（t_screening_hit_list）。 */
@Data
public class ScreeningHitList {
    /** 行 ID。 */
    private long hitListId;
    /** 命中 ID。 */
    private long hitId;
    /** 名单类型。 */
    private String listType;
    /** 创建 UTC 时间。 */
    private Instant createTime;
}
