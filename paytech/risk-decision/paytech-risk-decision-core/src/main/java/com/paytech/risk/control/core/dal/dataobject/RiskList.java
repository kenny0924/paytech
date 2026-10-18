package com.paytech.risk.control.core.dal.dataobject;

import java.time.Instant;

import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.core.enums.RiskListStatus;
import com.paytech.risk.control.domain.list.RiskSubjectType;

import lombok.Data;

/** 风险名单定义（t_risk_list）。 */
@Data
public class RiskList {
    /** 名单 ID。 */
    private long listId;

    /**
     * 名单编码
     * <li>全库唯一，创建后不可修改。</li>
     */
    private String listCode;

    /** 名单名称。 */
    private String listName;

    /** 名单唯一支持的主体类型。 */
    private RiskSubjectType supportedSubjectType;

    /**
     * 匹配方式
     * <li>一期固定为 EXACT：规范化主体键完全相等。</li>
     */
    private String matchingMode;

    /** 名单用途。 */
    private RiskListPurpose listPurpose;

    /** 名单状态。 */
    private RiskListStatus status;

    /** 名单并发版本，从 1 开始。 */
    private int version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
