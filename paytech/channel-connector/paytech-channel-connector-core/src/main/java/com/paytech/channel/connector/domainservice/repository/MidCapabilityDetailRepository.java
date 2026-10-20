package com.paytech.channel.connector.domainservice.repository;

import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;

/** MidCapabilityDetail 单表只读仓储。 */
public interface MidCapabilityDetailRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<MidCapabilityDetail> findByCapabilityIds(List<Long> ids);

    // ---- CC-B 追加：能力明细整组写入 ----

    /** 批量写入同一能力组的明细；行数与条数不符按系统错误失败。 */
    void insertAll(List<MidCapabilityDetail> details);

    /** 删除能力组的全部明细。 */
    void deleteByCapabilityId(long midCapabilityId);
}
