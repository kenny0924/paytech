package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 整体排序后一个键类型的新顺序号与新版本。 */
@Getter
@Setter
public class QueryKeyTypeOrderResult {

    /** 键类型 ID，十进制字符串。 */
    private String keyTypeId;

    /** 新顺序号：序号 × 10。 */
    private int sortNo;

    /** 新版本，下一次写操作按此传 expectedVersion。 */
    private int version;
}
