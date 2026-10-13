package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DataClassification;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import java.time.Instant;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 事件点版本允许引用的工程注册 Context Field。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class DecisionPointInputField {
    /** 事件点输入字段的稳定业务标识。 */
    private final long inputFieldId;

    /** 所属事件点版本的稳定业务标识。 */
    private final long pointVersionId;

    /** Risk 受控目录中的字段编码。 */
    @NonNull
    private final String fieldCode;

    /** 字段的闭合值类型。 */
    @NonNull
    private final DecisionValueType valueType;

    /** 字段是否为输入必填项。 */
    private final boolean requiredFlag;

    /** 字段的数据分类。 */
    @NonNull
    private final DataClassification dataClassification;

    /** 字段是否允许被规则引用。 */
    private final boolean ruleReferenceFlag;

    /** 字段是否允许作为 Action 参数。 */
    private final boolean actionParameterFlag;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;
}
