package com.paytech.risk.test.builder.decision;

import static com.paytech.risk.decision.domain.enums.VariableUsage.FLOW_CONDITION;
import static com.paytech.risk.decision.domain.enums.VariableUsage.RULE_CONDITION;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.decision.domain.enums.VariableUsage;

/**
 * 统一变量根及集合成员的测试 Builder
 * <li>同时作为夹具插入规格和响应/落库断言的期望值；编码由 Mocker 分配，不在 Builder 中生成。</li>
 * <li>常量值与成员值使用 Wire 规范形式：字符串类型为规范字符串，BOOLEAN 为 Boolean。</li>
 * <li>成员规范键按合同"数字去尾零、其他保持原文"预先计算，仅用于准备合法夹具。</li>
 *
 * @author Kenny
 */
public final class VariableBuilder {
    /** 种子数据中唯一已支持的决策点。 */
    public static final String POINT_CODE = "CHECKOUT_PREPARE";

    /** 种子数据中 ORDER_PREPARATION 当前合同（v5）的币种输入变量。 */
    public static final String INPUT_CURRENCY = "INPUT_CURRENCY";

    /** 夹具创建时间，UTC 微秒精度。 */
    public static final Instant CREATE_TIME = Instant.parse("2026-02-03T04:05:06.123456Z");

    /** 夹具最近更新时间。 */
    public static final Instant UPDATE_TIME = Instant.parse("2026-02-04T05:06:07.654321Z");

    /** 一个集合成员的规范值、规范键与展示名称。 */
    public record MemberSpec(Object value, String valueKey, String displayName) {
    }

    private String variableName = "Mock variable";

    private String description = "Mock description";

    private VariableSource sourceType;

    private DecisionValueType valueType;

    private String pointCode = POINT_CODE;

    private String sourceContractCode;

    private Integer sourceContractVersion;

    private String sourceField;

    private Set<VariableUsage> usages = EnumSet.of(RULE_CONDITION, FLOW_CONDITION);

    private boolean systemManaged;

    private VariableFormat formatCode;

    private NumericUnit numericUnit;

    private String currencyCode;

    private String currencyVariableCode;

    private String allowedValuesVariableCode;

    private Object currentValue;

    private VariableStatus status = VariableStatus.ACTIVE;

    private long version = 1;

    private long createBy = 70011L;

    private long updateBy = 70012L;

    private Instant createTime = CREATE_TIME;

    private Instant updateTime = UPDATE_TIME;

    private List<MemberSpec> members = new ArrayList<>();

    private VariableBuilder() {}

    /** 常量：指定类型、格式与规范当前值。 */
    public static VariableBuilder constant(DecisionValueType type, VariableFormat format, Object value) {
        VariableBuilder builder = new VariableBuilder();
        builder.sourceType = VariableSource.CONSTANT;
        builder.valueType = type;
        builder.formatCode = format;
        builder.currentValue = value;
        return builder;
    }

    /** TEXT 格式的字符串常量。 */
    public static VariableBuilder constantText(String value) {
        return constant(DecisionValueType.STRING, VariableFormat.TEXT, value);
    }

    /** 普通数值常量；值为八位小数规范形式。 */
    public static VariableBuilder constantNumber(String value) {
        return constant(DecisionValueType.NUMBER, VariableFormat.NONE, value).numericUnit(NumericUnit.SCALAR);
    }

    /** 指定成员类型与格式的空集合。 */
    public static VariableBuilder collection(DecisionValueType type, VariableFormat format) {
        VariableBuilder builder = new VariableBuilder();
        builder.sourceType = VariableSource.COLLECTION;
        builder.valueType = type;
        builder.formatCode = format;
        return builder;
    }

    /** TEXT 字符串集合。 */
    public static VariableBuilder textCollection() {
        return collection(DecisionValueType.STRING, VariableFormat.TEXT);
    }

    /** ORDER_PREPARATION 当前合同（v5）指定字段的输入别名。 */
    public static VariableBuilder input(String field, DecisionValueType type, VariableFormat format) {
        VariableBuilder builder = new VariableBuilder();
        builder.sourceType = VariableSource.INPUT;
        builder.valueType = type;
        builder.formatCode = format;
        builder.sourceContractCode = "ORDER_PREPARATION";
        builder.sourceContractVersion = RuleSetBuilder.CURRENT_CONTRACT_VERSION;
        builder.sourceField = field;
        return builder;
    }

    /** ORDER_PREPARATION 当前合同的消费者国家输入别名。 */
    public static VariableBuilder countryInput() {
        return input("consumer_country_code", DecisionValueType.STRING, VariableFormat.UPPER_ALPHA_2);
    }

    /** ORDER_PREPARATION 当前合同的币种输入别名，可作为金额的币种来源。 */
    public static VariableBuilder currencyInput() {
        return input("currency", DecisionValueType.STRING, VariableFormat.UPPER_ALPHA_3);
    }

    /** RULE_SET_NODE_RESULT v1 的 matched 节点输出，仅可用于流程条件。 */
    public static VariableBuilder nodeMatched() {
        VariableBuilder builder = new VariableBuilder();
        builder.sourceType = VariableSource.NODE_OUTPUT;
        builder.valueType = DecisionValueType.BOOLEAN;
        builder.formatCode = VariableFormat.NONE;
        builder.sourceContractCode = "RULE_SET_NODE_RESULT";
        builder.sourceContractVersion = 1;
        builder.sourceField = "matched";
        builder.usages = EnumSet.of(FLOW_CONDITION);
        builder.systemManaged = true;
        return builder;
    }

    /** 复制全部字段，供写操作基于夹具推导期望值。 */
    public VariableBuilder copy() {
        VariableBuilder copy = new VariableBuilder();
        copy.variableName = variableName;
        copy.description = description;
        copy.sourceType = sourceType;
        copy.valueType = valueType;
        copy.pointCode = pointCode;
        copy.sourceContractCode = sourceContractCode;
        copy.sourceContractVersion = sourceContractVersion;
        copy.sourceField = sourceField;
        copy.usages = EnumSet.copyOf(usages.isEmpty() ? EnumSet.noneOf(VariableUsage.class) : usages);
        copy.systemManaged = systemManaged;
        copy.formatCode = formatCode;
        copy.numericUnit = numericUnit;
        copy.currencyCode = currencyCode;
        copy.currencyVariableCode = currencyVariableCode;
        copy.allowedValuesVariableCode = allowedValuesVariableCode;
        copy.currentValue = currentValue;
        copy.status = status;
        copy.version = version;
        copy.createBy = createBy;
        copy.updateBy = updateBy;
        copy.createTime = createTime;
        copy.updateTime = updateTime;
        copy.members = new ArrayList<>(members);
        return copy;
    }

    public VariableBuilder variableName(String value) {
        this.variableName = value;
        return this;
    }

    public VariableBuilder description(String value) {
        this.description = value;
        return this;
    }

    public VariableBuilder pointCode(String value) {
        this.pointCode = value;
        return this;
    }

    public VariableBuilder usages(VariableUsage... values) {
        this.usages = values.length == 0 ? EnumSet.noneOf(VariableUsage.class) : EnumSet.of(values[0], values);
        return this;
    }

    /** 工程合同登记的系统定义。 */
    public VariableBuilder systemDefinition() {
        this.systemManaged = true;
        return this;
    }

    public VariableBuilder numericUnit(NumericUnit value) {
        this.numericUnit = value;
        return this;
    }

    public VariableBuilder currencyCode(String value) {
        this.currencyCode = value;
        return this;
    }

    public VariableBuilder currencyVariableCode(String value) {
        this.currencyVariableCode = value;
        return this;
    }

    public VariableBuilder allowedValuesVariableCode(String value) {
        this.allowedValuesVariableCode = value;
        return this;
    }

    public VariableBuilder currentValue(Object value) {
        this.currentValue = value;
        return this;
    }

    public VariableBuilder status(VariableStatus value) {
        this.status = value;
        return this;
    }

    public VariableBuilder version(long value) {
        this.version = value;
        return this;
    }

    public VariableBuilder createBy(long value) {
        this.createBy = value;
        return this;
    }

    public VariableBuilder updateBy(long value) {
        this.updateBy = value;
        return this;
    }

    public VariableBuilder updateTime(Instant value) {
        this.updateTime = value;
        return this;
    }

    /** 经接口新建后的期望：版本 1、创建与更新主体为操作者、时间为服务端命令时间、集合为空。 */
    public VariableBuilder created(long actorId) {
        VariableBuilder copy = copy();
        copy.version = 1;
        copy.createBy = actorId;
        copy.updateBy = actorId;
        copy.createTime = null;
        copy.updateTime = null;
        copy.members = new ArrayList<>();
        return copy;
    }

    /** 经接口成功修改后的期望：根版本加一、更新主体为操作者、更新时间为服务端命令时间。 */
    public VariableBuilder updated(long actorId) {
        VariableBuilder copy = copy();
        copy.version = version + 1;
        copy.updateBy = actorId;
        copy.updateTime = null;
        return copy;
    }

    /** 追加一个规范成员；数字规范键去尾零，其他类型保持原文。 */
    public VariableBuilder member(Object value, String displayName) {
        String key = valueType == DecisionValueType.NUMBER ? new BigDecimal((String) value).stripTrailingZeros().toPlainString()
            : value.toString();
        members.add(new MemberSpec(value, key, displayName));
        return this;
    }

    /** 替换全部成员，供整批替换后的期望值使用。 */
    public VariableBuilder members(List<MemberSpec> values) {
        this.members = new ArrayList<>(values);
        return this;
    }

    public String variableName() {
        return variableName;
    }

    public String description() {
        return description;
    }

    public VariableSource sourceType() {
        return sourceType;
    }

    public DecisionValueType valueType() {
        return valueType;
    }

    public String pointCode() {
        return pointCode;
    }

    public String sourceContractCode() {
        return sourceContractCode;
    }

    public Integer sourceContractVersion() {
        return sourceContractVersion;
    }

    public String sourceField() {
        return sourceField;
    }

    public Set<VariableUsage> usages() {
        return Set.copyOf(usages);
    }

    public boolean systemManaged() {
        return systemManaged;
    }

    public VariableFormat formatCode() {
        return formatCode;
    }

    public NumericUnit numericUnit() {
        return numericUnit;
    }

    public String currencyCode() {
        return currencyCode;
    }

    public String currencyVariableCode() {
        return currencyVariableCode;
    }

    public String allowedValuesVariableCode() {
        return allowedValuesVariableCode;
    }

    public Object currentValue() {
        return currentValue;
    }

    public VariableStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public long createBy() {
        return createBy;
    }

    public long updateBy() {
        return updateBy;
    }

    public Instant createTime() {
        return createTime;
    }

    public Instant updateTime() {
        return updateTime;
    }

    public List<MemberSpec> members() {
        return List.copyOf(members);
    }
}
