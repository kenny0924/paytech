package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import java.math.BigDecimal;
import com.paytech.risk.decision.domain.enums.*;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.variable.RiskVariable.Entry;
import com.paytech.risk.decision.domain.variable.VariableDefinitionValidator;
import com.paytech.risk.decision.domain.variable.VariableChange;
import com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.*;
import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import java.util.Objects;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

/** 统一变量的持久化记录，限 decision Owner JAR 内部使用。 */
@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class RiskVariableDefinition {
    /** 变量的稳定标识，由 Risk 分配；不作为共享值版本。 */
    private long variableId;

    /** 全库唯一且创建后不可修改、不可复用的变量编码，格式为大写字母开头的大写字母数字下划线。取值来源：Risk 统一变量库；非固定枚举。 */
    private String variableCode;

    /** 运营展示名称，可修改；不参与规则或流程内容 Hash。 */
    private String variableName;

    /** 变量用途的非敏感说明；未填写时为空。 */
    private String description;

    /** 来源类型，允许 INPUT、CONSTANT、COLLECTION、NODE_OUTPUT；创建后固定。允许值：INPUT、CONSTANT、COLLECTION、NODE_OUTPUT。 */
    private VariableSource sourceType;

    /** 标量类型或集合成员类型，允许 STRING、NUMBER、BOOLEAN、DATETIME、DURATION；创建后固定。允许值：STRING、NUMBER、BOOLEAN、DATETIME、DURATION。 */
    private DecisionValueType valueType;

    /** 适用决策点；INPUT 和 NODE_OUTPUT 必填，常量和集合为空表示全部已支持决策点。取值来源：Risk 决策点目录；非固定枚举。 */
    private String pointCode;

    /** INPUT 的输入合同或 NODE_OUTPUT 的输出合同代码；其他来源为空。取值来源：Risk 精确输入或节点输出合同；非固定枚举。 */
    private String sourceContractCode;

    /** 来源合同的准确版本；仅 INPUT 和 NODE_OUTPUT 非空，不取最新版本兜底。 */
    private Integer sourceContractVersion;

    /** 来源合同中的准确顶层字段键；仅 INPUT 和 NODE_OUTPUT 非空，不接受任意路径、SQL 或脚本。 */
    private String sourceField;

    /** 是否可用于规则条件；0 或 1，受来源合同允许用途约束。 */
    private boolean ruleConditionFlag;

    /** 是否可用于流程连线条件；0 或 1，节点输出仅当前规则集节点出线可用。 */
    private boolean flowConditionFlag;

    /** 是否可作为既有动作主体输入；0 或 1，本期只开放 INPUT 的批准主体槽位。 */
    private boolean actionParameterFlag;

    /** 是否为工程合同保留定义；0 或 1，系统定义的结构、状态、删除及系统集合成员不开放运营修改。 */
    private boolean systemManagedFlag;

    /** 通用格式配置，允许 TEXT、UPPER_ALPHA_2、UPPER_ALPHA_3、DIGITS_4、TOKEN、BCP47、POSITIVE_ID、NONE；具体变量的关联由本行管理。允许值：TEXT、UPPER_ALPHA_2、UPPER_ALPHA_3、DIGITS_4、TOKEN、BCP47、POSITIVE_ID、NONE。 */
    private VariableFormat formatCode;

    /** NUMBER 的量纲，允许 SCALAR 或 MONEY；其他类型为空。允许值：SCALAR、MONEY。 */
    private NumericUnit numericUnit;

    /** MONEY 的固定币种，三位大写字母；与 currency_variable_code 二选一，非 MONEY 为空。取值来源：ISO 4217 币种代码；非固定枚举。 */
    private String currencyCode;

    /** MONEY 的币种来源变量编码，指向同决策点 INPUT STRING 币种变量；与 currency_code 二选一，非 MONEY 为空。取值来源：Risk 统一变量库的币种输入；非固定枚举。 */
    private String currencyVariableCode;

    /** 可选值来源集合变量编码，必须同成员类型且适用范围覆盖本变量；为空表示无额外枚举范围，集合本身不再引用集合。取值来源：Risk 统一变量库的集合；非固定枚举。 */
    private String allowedValuesVariableCode;

    /** CONSTANT STRING 的当前值；仅匹配来源和值类型时非空，不接受敏感原值。 */
    private String stringValue;

    /** CONSTANT NUMBER 的当前精确数值；仅匹配来源和值类型时非空。 */
    private BigDecimal numberValue;

    /** CONSTANT BOOLEAN 的当前值，0 或 1；仅匹配来源和值类型时非空。 */
    private Boolean booleanValue;

    /** CONSTANT DATETIME 的当前 UTC 微秒值；仅匹配来源和值类型时非空。 */
    private Instant datetimeValue;

    /** CONSTANT DURATION 的当前非负毫秒值；仅匹配来源和值类型时非空。 */
    private Long durationValue;

    /** 当前状态，允许 ACTIVE、DISABLED、DELETED；停用禁止新保存引用，删除仅无任何引用时允许且保留编码。允许值：ACTIVE、DISABLED、DELETED。 */
    private VariableStatus status;

    /** 变量根的正整数 CAS 版本，定义、值或集合成员成功变更时加一。 */
    private long version;

    /** 创建主体的稳定账号或正式系统主体标识。 */
    private long createBy;

    /** 最近修改主体的稳定账号或正式系统主体标识。 */
    private long updateBy;

    /** Risk 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 变量定义或成员最近成功更新的 UTC 微秒时间。 */
    private Instant updateTime;

    /** 校验持久化结构、类型槽位和审计元数据，损坏不得降级为空变量。 */
    public void verifyIntegrity() {
        try {
            Entry entry = RiskVariablePersistenceConverter.entry(this);
            VariableDefinitionValidator.validate(entry.definition());
            if (status == null || version <= 0 || createBy <= 0 || updateBy <= 0 || createTime == null || updateTime == null)
                throw invalid("变量根元数据无效");
            if (!Objects.equals(currentValue(entry.definition(), entry.currentValue()), entry.currentValue()))
                throw invalid("变量值未规范化");
        } catch (RiskVariableException failure) {
            throw new ErrorException("变量定义损坏：" + variableCode, failure);
        }
    }

    /** 墓碑不可再写，用户确认的版本必须与锁定根相同。 */
    public void verifyMutable(long expectedVersion) {
        if (status == VariableStatus.DELETED) throw failure(VARIABLE_NOT_FOUND, "变量已删除");
        if (expectedVersion <= 0 || expectedVersion != version)
            throw failure(VARIABLE_VERSION_CONFLICT, "变量已改变，请刷新后确认");
        if (version == Long.MAX_VALUE) throw new ErrorException("变量版本已达上限");
    }

    /** 集合成员能力只能用于未删除的集合。 */
    public void verifyCollection() {
        if (status == VariableStatus.DELETED) throw failure(VARIABLE_NOT_FOUND, "变量已删除");
        if (sourceType != VariableSource.COLLECTION) throw failure(VARIABLE_TYPE_MISMATCH, "该变量不是集合");
    }

    /** 系统定义不能删除或替换成员。 */
    public void verifyOperatorManaged() {
        if (systemManagedFlag) throw failure(SYSTEM_VARIABLE_READ_ONLY, "系统变量不开放此操作");
    }

    /** 系统定义仅开放名称与说明，状态和值保持原事实。 */
    public void verifyUpdate(Object value, VariableStatus nextStatus) {
        if (nextStatus == null || nextStatus == VariableStatus.DELETED) throw invalid("更新状态只能为 ACTIVE 或 DISABLED");
        if (systemManagedFlag && (nextStatus != status
            || !Objects.equals(value, RiskVariablePersistenceConverter.entry(this).currentValue())))
            throw failure(SYSTEM_VARIABLE_READ_ONLY, "系统定义只允许修改名称和说明");
    }

    /** SQL 行数确认后回写本对象，失败的事务必须丢弃整个 Context。 */
    public void apply(VariableChange change, long actorId, Instant time) {
        variableName = change.getVariableName(); description = change.getDescription();
        stringValue = change.getValues().stringValue(); numberValue = change.getValues().numberValue();
        booleanValue = change.getValues().booleanValue(); datetimeValue = change.getValues().datetimeValue();
        durationValue = change.getValues().durationValue(); status = change.getStatus();
        version++; updateBy = actorId; updateTime = time;
    }
}
