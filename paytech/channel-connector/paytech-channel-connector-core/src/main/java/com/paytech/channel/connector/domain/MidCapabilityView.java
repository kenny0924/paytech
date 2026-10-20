package com.paytech.channel.connector.domain;

import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 能力组与其明细的组合结果。 */
@Getter
@AllArgsConstructor
public class MidCapabilityView {
    /** 能力组。 */
    private final MidCapability capability;

    /** 能力组明细，按能力代码升序。 */
    private final List<MidCapabilityDetail> details;
}
