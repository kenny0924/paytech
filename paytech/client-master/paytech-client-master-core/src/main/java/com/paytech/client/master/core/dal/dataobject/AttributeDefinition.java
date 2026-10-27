package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.AttributeDataType;
import com.paytech.client.master.domain.enums.AttributeSensitivity;
import com.paytech.client.master.domain.enums.CatalogStatus;
import com.paytech.client.master.domain.enums.ClientType;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 扩展属性定义（t_attribute_definition）。 */
@Getter
@Setter
public class AttributeDefinition {
    /** 属性代码。 */
    private String attributeCode;
    /** 适用辖区；为空表示全局。 */
    private String jurisdictionCode;
    /** 适用主体类型。 */
    private ClientType partyType;
    /** 取值类型。 */
    private AttributeDataType dataType;
    /** CODE 类型的取值目录，可空。 */
    private String catalogCode;
    /** 敏感级别。 */
    private AttributeSensitivity sensitivity;
    /** VAULT 级的 Vault 数据类型，可空。 */
    private String vaultDataType;
    /** TEXT 最大字符数，可空。 */
    private Integer maxLength;
    /** TEXT 格式正则，可空。 */
    private String formatRegex;
    /** 展示名称文案键。 */
    private String labelKey;
    /** 状态。 */
    private CatalogStatus status;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
