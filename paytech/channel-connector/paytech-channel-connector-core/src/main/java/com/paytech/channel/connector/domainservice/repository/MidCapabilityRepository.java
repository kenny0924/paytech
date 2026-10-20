package com.paytech.channel.connector.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.enums.CapabilityStatus;

/** MidCapability 单表只读仓储。 */
public interface MidCapabilityRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<MidCapability> findByMidIds(List<Long> ids);

    // ---- CC-B 追加：能力组管理写入 ----

    /** 按主键读取能力组，未找到返回空。 */
    MidCapability findById(long id);

    /** 写入新能力组；六维组合重复按并发冲突拒绝。 */
    void insert(MidCapability capability);

    /** 以原对象当前状态为条件启停；行数不为 1 按并发冲突拒绝，成功后同步原对象。 */
    void updateStatus(MidCapability capability, CapabilityStatus toStatus, Instant now);

    /** 刷新修改时间；行数不为 1 按系统错误失败，成功后同步原对象。 */
    void touch(MidCapability capability, Instant now);
}
