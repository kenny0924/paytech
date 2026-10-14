package com.paytech.client.contract.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 一条运营分类（t_client_business_classification）。 */
@Getter
@Setter
public class ClientClassification {
    /** 分类 ID。 */
    private long classificationId;
    /** 经营资料 ID。 */
    private long businessProfileId;
    /** 行业代码。 */
    private String industryCode;
    /** 四位 MCC。 */
    private String mccCode;
    /** 主要分类为 1，其他为空。 */
    private Integer primarySlot;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;

    /** 是否主要分类。 */
    public boolean isPrimary() {
        return Integer.valueOf(1).equals(primarySlot);
    }
}
