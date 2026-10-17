package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import java.math.BigDecimal;
import com.paytech.risk.decision.domain.enums.*;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

/** 统一变量的持久化记录，限 decision Owner JAR 内部使用。 */
@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class RiskVariableMember {
    /** 集合成员行标识，整体替换时可重新分配；不是变量版本。 */
    private long memberId;

    /** 所属 COLLECTION 变量编码，成员类型和量纲由变量根统一拥有。取值来源：Risk 统一变量库；非固定枚举。 */
    private String variableCode;

    /** 成员按通用类型规范化后的精确字符串键，用于唯一去重；不包含 Hash，字符串大小写及前导零保留。 */
    private String valueKey;

    /** STRING 成员值；仅该类型非空。 */
    private String stringValue;

    /** NUMBER 成员精确数值；仅该类型非空。 */
    private BigDecimal numberValue;

    /** BOOLEAN 成员值，0 或 1；仅该类型非空。 */
    private Boolean booleanValue;

    /** DATETIME 成员 UTC 微秒值；仅该类型非空。 */
    private Instant datetimeValue;

    /** DURATION 成员非负毫秒值；仅该类型非空。 */
    private Long durationValue;

    /** 成员的可选展示名称，不改变成员业务值；未填写时为空。 */
    private String displayName;

    /** 成员在管理与选项中的展示顺序，正整数；不参与集合包含判断。 */
    private int sortOrder;

    /** 该成员行在 Risk 数据库中的 UTC 微秒写入时间。 */
    private Instant createTime;
}
