package com.paytech.client.master.domainservice.repository;

import java.time.Instant;

/** t_business_change_version 的本地仓储；只追加，不负责其他表或事务。 */
public interface BusinessChangeVersionRepository {
    /** 追加一条 UPDATE 快照；同根同版本已存在时由 uk_bcv_history_ver 拒绝并整体回滚。 */
    void insertUpdate(long changeVersionId, long historyId, long versionNo, String snapshotJson, long createBy, Instant now);
}
