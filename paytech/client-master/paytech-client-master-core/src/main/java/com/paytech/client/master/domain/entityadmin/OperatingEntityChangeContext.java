package com.paytech.client.master.domain.entityadmin;

import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.OperatingEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改 OE 收单资质：已读取的 OE 原记录、目标资质与根推进。 */
@Getter
@AllArgsConstructor
public class OperatingEntityChangeContext {
    /** 修改前的 OE。 */
    private final OperatingEntity entity;
    /** 目标收单资质。 */
    private final AcquiringQualification acquiringQualification;
    /** OPERATING_ENTITY 根推进。 */
    private final RootRevision revision;
}
