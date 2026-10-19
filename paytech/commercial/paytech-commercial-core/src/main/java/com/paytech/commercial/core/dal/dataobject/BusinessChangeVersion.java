package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.domain.enums.BusinessChangeOperation;
import com.paytech.commercial.domain.enums.BusinessChangeSnapshotFormat;

import lombok.Getter;
import lombok.Setter;

/**
 * t_business_change_version：不可变的行级审计快照，只插入、不更新。
 * <li>uk_bcv_history_ver(history_id, version_no)；snapshot_json 为按 record_type 白名单生成的 _meta + data 信封。</li>
 */
@Getter
@Setter
public class BusinessChangeVersion {
    /** 快照的内部唯一标识 */
    private Long changeVersionId;

    /** 所属历史身份 */
    private Long historyId;

    /**
     * 本次变化提交后的聚合根版本坐标
     * <li>同一业务行允许跳号。</li>
     */
    private Long versionNo;

    /** 行操作类型 */
    private BusinessChangeOperation operationType;

    /** 不可变 JSON 审计视图，含完整性哈希 */
    private String snapshotJson;

    /** 快照格式；新快照固定 ENTITY_NO */
    private BusinessChangeSnapshotFormat snapshotFormat;

    /** 本次变更的操作者 */
    private Long createBy;

    /** 快照创建的 UTC 微秒时间 */
    private Instant createTime;
}
