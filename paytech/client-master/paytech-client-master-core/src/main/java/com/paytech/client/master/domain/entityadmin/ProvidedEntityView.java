package com.paytech.client.master.domain.entityadmin;

import java.time.Instant;

import com.paytech.client.master.domain.enums.EntitySourceRelationStatus;
import com.paytech.client.master.domain.enums.OperatingEntityStatus;

import lombok.Getter;
import lombok.Setter;

/** Partner 以 PARTNER 来源关系提供的一个经营主体（来源关系 + OE 主档 + 法定名称）。 */
@Getter
@Setter
public class ProvidedEntityView {
    /** OE 编号。 */
    private String entityNo;
    /** OE 法定名称；资料缺失时为空。 */
    private String legalName;
    /** OE 状态。 */
    private OperatingEntityStatus entityStatus;
    /** 来源关系 ID。 */
    private Long sourceRelationId;
    /** 来源关系状态。 */
    private EntitySourceRelationStatus sourceStatus;
    /** 生效开始时间。 */
    private Instant effectiveFrom;
    /** 生效结束时间。 */
    private Instant effectiveTo;
}
