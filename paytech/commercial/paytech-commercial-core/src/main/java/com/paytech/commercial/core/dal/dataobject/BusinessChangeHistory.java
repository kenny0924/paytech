package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;

import lombok.Getter;
import lombok.Setter;

/** t_business_change_record 完整行：白名单业务对象的稳定审计身份与当前修订；Checkout 只读投影另见 CheckoutRecords。 */
@Getter
@Setter
public class BusinessChangeHistory {
    /** 审计身份 ID */
    private Long historyId;

    /** 审计对象类型 */
    private BusinessChangeRecordRecordType recordType;

    /** 审计对象业务 ID（十进制字符串） */
    private String recordId;

    /** 所属聚合根类型 */
    private BusinessChangeRecordAggregateRootType aggregateRootType;

    /** 所属聚合根 ID（十进制字符串） */
    private String aggregateRootId;

    /**
     * 直接父审计身份
     * <li>Root 为空；Child 指向 Root 的 history_id。</li>
     */
    private Long parentHistoryId;

    /**
     * 当前修订
     * <li>Root 为 expected_change_version CAS 令牌；Child 指向最近一次快照采用的 Root 版本。</li>
     */
    private Long currentVersion;

    /** 创建时间（UTC） */
    private Instant createTime;

    /** 更新时间（UTC） */
    private Instant updateTime;
}
