package com.paytech.platform.support.referencedata.client;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/** 快照中的一条参考数据：按编码唯一，带 Platform 当前状态。 */
public interface ReferenceItem {

    /**
     * 参考数据编码
     *
     * @return 该种类内唯一的编码，例如币种 USD、国家 AE、MCC 5734
     */
    String code();

    /**
     * Platform 当前状态
     *
     * @return DRAFT、ACTIVE 或 INACTIVE；只有 ACTIVE 允许被新业务选择
     */
    ReferenceDataStatus status();
}
