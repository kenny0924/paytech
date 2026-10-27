package com.paytech.compliance.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 结束进行中案件时写入的说明、退回期限与操作方。 */
@Getter
@AllArgsConstructor
public class CaseClosing {
    /** 客户可见说明。 */
    private final String publicNote;
    /** 内部理由。 */
    private final String resolutionNote;
    /** 兼容退回分区。 */
    private final String returnSection;
    /** 退回补件期限；非退回为空。 */
    private final Instant reviewDueTime;
    /** 操作方。 */
    private final String operatedBy;
    /** 代操作的责任 PayFac。 */
    private final Long onBehalfOfPartnerId;
}
