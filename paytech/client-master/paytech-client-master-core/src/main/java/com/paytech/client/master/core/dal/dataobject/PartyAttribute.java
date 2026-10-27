package com.paytech.client.master.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Party 资料扩展属性值（t_party_attribute）。 */
@Getter
@Setter
public class PartyAttribute {
    /** 属性值行 ID。 */
    private Long attributeId;
    /** 所属资料 ID。 */
    private Long profileId;
    /** 属性代码。 */
    private String attributeCode;
    /** TEXT、COUNTRY、CODE 类型取值。 */
    private String valueText;
    /** DATE 类型取值。 */
    private LocalDate valueDate;
    /** DECIMAL 类型取值。 */
    private BigDecimal valueDecimal;
    /** VAULT 级 Token；本期恒为空。 */
    private String valueVaultToken;
    /** VAULT 级脱敏值；本期恒为空。 */
    private String maskedValue;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
