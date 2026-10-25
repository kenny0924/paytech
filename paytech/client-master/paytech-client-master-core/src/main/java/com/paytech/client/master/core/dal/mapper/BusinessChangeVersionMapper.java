package com.paytech.client.master.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

/** t_business_change_version 的不可变快照写入。 */
public interface BusinessChangeVersionMapper {
    /** 追加一条 UPDATE 快照（ENTITY_NO 格式）。 */
    void insertUpdate(@Param("changeVersionId") long changeVersionId, @Param("historyId") long historyId,
        @Param("versionNo") long versionNo, @Param("snapshotJson") String snapshotJson, @Param("createBy") long createBy,
        @Param("now") Instant now);
}
