package com.paytech.platform.support.referencedata.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import lombok.Getter;
import lombok.Setter;

/** t_ref_mcc 映射对象；只在 referencedata 模块内使用。 */
@Getter
@Setter
public class Mcc {

    /**
     * MCC 主键
     * <li>初始化数据使用 5001 起的固定 ID。</li>
     */
    private Long id;

    /** 四位 MCC 编码，保留前导零。 */
    private String mccCode;

    /** 英文短名。 */
    private String name;

    /** 标准英文描述。 */
    private String description;

    /** 是否卡组织公布的高风险类目。 */
    private Boolean networkHighRisk;

    /** 状态。 */
    private ReferenceDataStatus status;

    /** 乐观并发版本，从 1 开始。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
