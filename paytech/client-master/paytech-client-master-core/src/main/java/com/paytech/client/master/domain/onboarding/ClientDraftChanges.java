package com.paytech.client.master.domain.onboarding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次草稿保存在事务外准备好的全部写入：资料列、标识（已规范化并按需 Protect）、扩展属性与 Schema 切换
 * <li>不含号码原值；只含 Token、公开登记号、脱敏值与检查结论。</li>
 */
@Getter
@AllArgsConstructor
public class ClientDraftChanges {
    /** 资料列修改，键为 分区_字段（如 individual_givenName、identifier_identifierVaultToken）。 */
    private final Map<String, Object> columns;
    /** 扩展属性修改：属性代码到类型化取值，null 表示清空。 */
    private final Map<String, AttributeValue> attributes;
    /** 标识分区是否修改；修改时先解除旧身份键绑定，提交时重新绑定。 */
    private final boolean identifierChanged;
    /** 更换后的组织类型；未更换为空。 */
    private final String organizationTypeCode;
    /** 按新组织类型重新解析的 Schema 版本；未更换为空。 */
    private final Long schemaId;

    /** 是否修改某分区的列。 */
    public boolean touches(String group) {
        return columns.keySet().stream().anyMatch(key -> key.startsWith(group + "_"));
    }

    /** 没有任何资料列、扩展属性、标识或 Schema 修改。 */
    public boolean isEmpty() {
        return columns.isEmpty() && attributes.isEmpty() && !identifierChanged && !schemaChanged();
    }

    /** 是否需要重新冻结 Schema。 */
    public boolean schemaChanged() {
        return schemaId != null;
    }

    /** 资料含受保护引用，不输出到日志。 */
    @Override
    public String toString() {
        return "ClientDraftChanges[redacted]";
    }

    /**
     * 扩展属性的类型化取值，恰好一项非空
     *
     * @param text TEXT、COUNTRY、CODE
     * @param date DATE
     * @param decimal DECIMAL
     */
    public record AttributeValue(String text, LocalDate date, BigDecimal decimal) {
    }
}
