package com.paytech.risk.decision.domain.dictionary;

import java.time.Instant;

import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.DictionaryType;

/** 字典业务当前值及命令模型；不暴露持久化主键。 */
public final class DecisionDictionary {
    /** 仅作为模型或转换方法的命名空间，不允许创建实例。 */
    private DecisionDictionary() { }
    /** 标签的 defaultMessage 始终为空；时间和账号属于当前记录。 */
    public record Entry(DictionaryType type, String code, String name, String description, String defaultMessage,
        DictionaryStatus status, long version, long createdBy, long updatedBy, Instant createTime, Instant updateTime) { }
    /** 保存可编辑字段，代码由独立业务坐标决定。 */
    public record Content(String name, String description, String defaultMessage) { }
    /** 单次事务的确定结果，未存储历史响应。 */
    public record WriteResult(DictionaryType type, String code, long version, DictionaryStatus status,
        long changedBy, Instant changedTime) { }
}
