package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;

import lombok.Getter;
import lombok.Setter;

/**
 * t_business_change_record：一条受审计业务行的稳定历史身份与当前修订。
 * <li>Root（Record Tuple 等于聚合根 Tuple 且无父记录）的 currentVersion 是聚合唯一 CAS 令牌；受审计 Child 只记录最近一次快照采用的
 * Root 版本，不做独立 CAS。身份、归属与父子关系创建后不可修改。Checkout 只读快照另用 CheckoutRecords.BusinessChangeRecord。</li>
 */
@Getter
@Setter
public class BusinessChangeRecord {
    /** 历史身份的内部唯一标识 */
    private Long historyId;

    /** 受审计对象类型 */
    private BusinessChangeRecordRecordType recordType;

    /** 被审计业务行在其类型下的稳定标识（十进制主键） */
    private String recordId;

    /** 所属聚合根类型 */
    private BusinessChangeRecordAggregateRootType aggregateRootType;

    /** 所属聚合根标识 */
    private String aggregateRootId;

    /**
     * 同一聚合内直接父记录的历史身份
     * <li>Root 为空。</li>
     */
    private Long parentHistoryId;

    /**
     * 当前修订
     * <li>Root 为聚合 CAS 令牌；Child 为最近一次快照采用的 Root 版本。</li>
     */
    private Long currentVersion;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
