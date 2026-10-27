package com.paytech.platform.support.referencedata.service.processor.response;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.Mcc;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 跨模块发布的 MCC 结果；不暴露 DataObject。 */
@Getter
@AllArgsConstructor
public class MccResult {

    /** 四位 MCC 编码。 */
    private final String mccCode;

    /** 英文短名。 */
    private final String name;

    /** 英文描述。 */
    private final String description;

    /** 是否卡组织公布的高风险类目。 */
    private final boolean networkHighRisk;

    /** 状态。 */
    private final ReferenceDataStatus status;

    /** 乐观并发版本。 */
    private final int version;

    /** 由本模块 DataObject 构造。 */
    public static MccResult of(Mcc row) {
        return new MccResult(row.getMccCode(), row.getName(), row.getDescription(),
            Boolean.TRUE.equals(row.getNetworkHighRisk()), row.getStatus(), row.getVersion());
    }
}
