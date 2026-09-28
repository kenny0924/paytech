package com.paytech.payment.channel.core.dal.dataobject;

import java.time.Instant;
import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;
import com.paytech.payment.channel.core.enums.CapabilityValueType;


/** MidCapabilityDetail 对应的 Channel 所属持久化对象。 */
@Getter
@Setter
public class MidCapabilityDetail {
    /** 能力明细标识。 */
    private Long midCapabilityDetailId;

    /** 所属能力条件组标识。 */
    private Long midCapabilityId;

    /** 开放的已注册能力目录代码。 */
    private String capabilityCode;

    /** 必须与能力目录一致的值类型。 */
    private CapabilityValueType valueType;

    /** BOOLEAN 时只能为 0 或 1，其他类型为空。 */
    private Integer booleanValue;

    /** DECIMAL 时为精确值，其他类型为空。 */
    private BigDecimal decimalValue;

    /** CODE 时为目录值，其他类型为空。 */
    private String codeValue;

    /** 创建 UTC 时间，微秒精度。 */
    private Instant createTime;

    /** 最后变更 UTC 时间，微秒精度。 */
    private Instant updateTime;
}
