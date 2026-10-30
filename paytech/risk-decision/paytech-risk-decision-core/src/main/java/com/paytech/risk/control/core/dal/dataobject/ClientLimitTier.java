package com.paytech.risk.control.core.dal.dataobject;

import java.time.Instant;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierSource;
import com.paytech.risk.control.core.enums.UnderwritingClientType;

import lombok.Data;

/** Client 当前限额档位（t_client_limit_tier），每个 Client 至多一行；无行表示无档位（额度为零）。 */
@Data
public class ClientLimitTier {
    /** 档位行 ID。 */
    private long clientLimitTierId;

    /** Client ID（client-master 跨库值引用），唯一。 */
    private long clientId;

    /** 当前档位。 */
    private LimitTier tier;

    /** 进入 RESTRICTED 前的档位，用于限制恢复目标；非 RESTRICTED 为空。 */
    private LimitTier previousTier;

    /** 建档时 Client 名称，仅供展示。 */
    private String clientDisplayName;

    /** 建档时 Client 编号，仅供展示与搜索。 */
    private String clientNo;

    /** 建档时 Client 类型。 */
    private UnderwritingClientType clientType;

    /** 建档时入驻辖区代码（开放目录）。 */
    private String jurisdictionCode;

    /** 当前档位的来源。 */
    private LimitTierSource sourceType;

    /** 产生当前档位的变更记录 ID。 */
    private long sourceChangeId;

    /** 当前档位生效 UTC 时间。 */
    private Instant effectiveTime;

    /** 观察期开始 UTC 时间；非 OBSERVATION 为空。 */
    private Instant observationStartedTime;

    /** 观察期目标天数 1—365；非 OBSERVATION 为空。 */
    private Integer observationTargetDays;

    /** 观察期目标放行笔数 1—100000；非 OBSERVATION 为空。 */
    private Integer observationTargetTransactions;

    /** 开始或延长当前观察期的变更记录 ID；非 OBSERVATION 为空。 */
    private Long observationStartedByChangeId;

    /** 建档操作人：承保初始化为承保决定人或触发人，迁移为执行人。 */
    private Long createdBy;

    /** 并发版本，初始 1，每次档位变化加一。 */
    private int version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
