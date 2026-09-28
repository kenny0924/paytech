package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DataClassification;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import java.time.Instant;
import java.util.Objects;

/** 事件点版本允许引用的工程注册 Context Field。 */
public record DecisionPointInputField(
    /** 事件点输入字段的稳定业务标识。 */
    long inputFieldId,
    /** 所属事件点版本的稳定业务标识。 */
    long pointVersionId,
    /** Risk 受控目录中的字段编码。 */
    String fieldCode,
    /** 字段的闭合值类型。 */
    DecisionValueType valueType,
    /** 字段是否为输入必填项。 */
    boolean requiredFlag,
    /** 字段的数据分类。 */
    DataClassification dataClassification,
    /** 字段是否允许被规则引用。 */
    boolean ruleReferenceFlag,
    /** 字段是否允许作为 Action 参数。 */
    boolean actionParameterFlag,
    /** 记录的 UTC 微秒创建时间。 */
    Instant createTime
) {
    public DecisionPointInputField {
        Objects.requireNonNull(fieldCode, "fieldCode");
        Objects.requireNonNull(valueType, "valueType");
        Objects.requireNonNull(dataClassification, "dataClassification");
        Objects.requireNonNull(createTime, "createTime");
    }
}
