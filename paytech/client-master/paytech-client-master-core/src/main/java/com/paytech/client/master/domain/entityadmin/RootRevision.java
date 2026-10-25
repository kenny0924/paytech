package com.paytech.client.master.domain.entityadmin;

import java.time.Instant;

import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次运营写入对所属 BCR 根的推进：根 CAS（expectedVersion → nextVersion）与 UPDATE 快照
 * <li>历史手工数据没有根时 rootMissing 为 true：同一事务先补建 V1 根再推进。</li>
 */
@Getter
@AllArgsConstructor
public class RootRevision {
    /** 根类型，同时是 aggregate_root_type。 */
    private final BusinessChangeRecordRecordType rootType;
    /** 根业务标识。 */
    private final String rootId;
    /** 根 history_id；缺根时为新分配的 ID。 */
    private final long historyId;
    /** 是否需要补建 V1 根。 */
    private final boolean rootMissing;
    /** 调用方读取时的根修订号。 */
    private final long expectedVersion;
    /** 新快照的 change_version_id。 */
    private final long changeVersionId;
    /** 已校验的 UPDATE 快照 JSON。 */
    private final String snapshotJson;
    /** 操作者 Account ID。 */
    private final long actorId;
    /** 服务端时间，微秒精度。 */
    private final Instant now;

    /** CAS 后的根修订号。 */
    public long nextVersion() {
        return expectedVersion + 1;
    }
}
