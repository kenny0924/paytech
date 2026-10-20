package com.paytech.channel.connector.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.enums.CapabilityStatus;

/** MidCapability SQL 参数绑定，SQL 仅位于 XML。 */
public interface MidCapabilityMapper {
    /** 按归属读取记录。 */
    List<MidCapability> findByMidIds(@Param("ids") List<Long> ids);

    // ---- CC-B 追加：能力组管理写入 ----

    /** 按主键读取能力组。 */
    MidCapability findById(@Param("id") long id);

    /** 插入一条新能力组。 */
    int insert(MidCapability record);

    /** 以期望原状态为条件启停能力组。 */
    int updateStatus(@Param("midCapabilityId") long midCapabilityId, @Param("fromStatus") CapabilityStatus fromStatus,
        @Param("toStatus") CapabilityStatus toStatus, @Param("now") Instant now);

    /** 明细整组替换后刷新能力组修改时间。 */
    int touch(@Param("midCapabilityId") long midCapabilityId, @Param("now") Instant now);
}
