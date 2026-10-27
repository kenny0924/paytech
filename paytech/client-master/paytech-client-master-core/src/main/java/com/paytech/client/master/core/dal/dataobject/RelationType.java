package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.CatalogStatus;
import com.paytech.client.master.domain.enums.RuleRole;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 主体关系类型目录行（t_relation_type）。 */
@Getter
@Setter
public class RelationType {
    /** 目录行 ID。 */
    private Long relationTypeId;
    /** 关系类型代码，全局唯一。 */
    private String relationTypeCode;
    /** 限定辖区；为空表示全局。 */
    private String jurisdictionCode;
    /** 关系人规则校验识别的角色。 */
    private RuleRole ruleRole;
    /** 是否计入受益人门槛与 100% 合计。 */
    private Boolean uboFlag;
    /** 是否必须填写持股比例。 */
    private Boolean ownershipPctRequired;
    /** 展示名称文案键。 */
    private String labelKey;
    /** 展示顺序。 */
    private Integer displayOrder;
    /** 状态。 */
    private CatalogStatus status;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
