package com.paytech.risk.decision.api;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 统一变量管理的轻量 HTTP 契约；不暴露 Mapper 或 DataObject。 */
public final class RiskVariableModels {
    /** 仅提供合同命名空间。 */
    private RiskVariableModels() { }

    /** 目录当前值的有限投影；输入和节点输出的 currentValue 恒为 null。 */
    public static class VariableSummary {

        /** 永久唯一变量编码。 */
        private String variableCode;

        /** 运营展示名称。 */
        private String variableName;

        /** 变量来源类型。 */
        private String sourceType;

        /** 变量或成员的标量类型。 */
        private String valueType;

        /** 适用决策点；全局变量可空。 */
        private String pointCode;

        /** 允许的引用用途。 */
        private List<String> usages;

        /** 是否为工程保留定义。 */
        private boolean systemManaged;

        /** 当前状态。 */
        private String status;

        /** 根 CAS 版本，以正整数字符串表示。 */
        private String version;

        /** NUMBER 的量纲；其他类型为空。 */
        private String numericUnit;

        /** 固定币种；与动态币种变量互斥。 */
        private String currencyCode;

        /** 动态币种变量编码；不适用时为空。 */
        private String currencyVariableCode;

        /** 通用值格式代码。 */
        private String formatCode;

        /** 可选值集合编码；无额外约束时为空。 */
        private String allowedValuesVariableCode;

        /** 常量当前值；仅为字符串或 Boolean，其他来源为空。 */
        private Object currentValue;

        /** 集合成员数量；非集合为空。 */
        private Long memberCount;

        /** 有界成员预览。 */
        private List<Member> previewMembers;

        /** UTC 微秒更新时间。 */
        private String updateTime;

        /** JSON 反序列化构造。 */
        public VariableSummary() { }

        /** 按完整合同构造。 */
        public VariableSummary(String variableCode, String variableName, String sourceType, String valueType, String pointCode, List<String> usages, boolean systemManaged, String status, String version, String numericUnit, String currencyCode, String currencyVariableCode, String formatCode, String allowedValuesVariableCode, Object currentValue, Long memberCount, List<Member> previewMembers, String updateTime) {
            this.variableCode = variableCode;
            this.variableName = variableName;
            this.sourceType = sourceType;
            this.valueType = valueType;
            this.pointCode = pointCode;
            this.usages = usages;
            this.systemManaged = systemManaged;
            this.status = status;
            this.version = version;
            this.numericUnit = numericUnit;
            this.currencyCode = currencyCode;
            this.currencyVariableCode = currencyVariableCode;
            this.formatCode = formatCode;
            this.allowedValuesVariableCode = allowedValuesVariableCode;
            this.currentValue = currentValue;
            this.memberCount = memberCount;
            this.previewMembers = previewMembers;
            this.updateTime = updateTime;
        }

        /** 读取永久唯一变量编码。 */
        public String getVariableCode() { return variableCode; }

        /** 设置永久唯一变量编码。 */
        public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

        /** 读取运营展示名称。 */
        public String getVariableName() { return variableName; }

        /** 设置运营展示名称。 */
        public void setVariableName(String variableName) { this.variableName = variableName; }

        /** 读取变量来源类型。 */
        public String getSourceType() { return sourceType; }

        /** 设置变量来源类型。 */
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }

        /** 读取变量或成员的标量类型。 */
        public String getValueType() { return valueType; }

        /** 设置变量或成员的标量类型。 */
        public void setValueType(String valueType) { this.valueType = valueType; }

        /** 读取适用决策点。 */
        public String getPointCode() { return pointCode; }

        /** 设置适用决策点。 */
        public void setPointCode(String pointCode) { this.pointCode = pointCode; }

        /** 读取允许的引用用途。 */
        public List<String> getUsages() { return usages; }

        /** 设置允许的引用用途。 */
        public void setUsages(List<String> usages) { this.usages = usages; }

        /** 读取是否为工程保留定义。 */
        public boolean getSystemManaged() { return systemManaged; }

        /** 设置是否为工程保留定义。 */
        public void setSystemManaged(boolean systemManaged) { this.systemManaged = systemManaged; }

        /** 读取当前状态。 */
        public String getStatus() { return status; }

        /** 设置当前状态。 */
        public void setStatus(String status) { this.status = status; }

        /** 读取根 CAS 版本，以正整数字符串表示。 */
        public String getVersion() { return version; }

        /** 设置根 CAS 版本，以正整数字符串表示。 */
        public void setVersion(String version) { this.version = version; }

        /** 读取NUMBER 的量纲。 */
        public String getNumericUnit() { return numericUnit; }

        /** 设置NUMBER 的量纲。 */
        public void setNumericUnit(String numericUnit) { this.numericUnit = numericUnit; }

        /** 读取固定币种。 */
        public String getCurrencyCode() { return currencyCode; }

        /** 设置固定币种。 */
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

        /** 读取动态币种变量编码。 */
        public String getCurrencyVariableCode() { return currencyVariableCode; }

        /** 设置动态币种变量编码。 */
        public void setCurrencyVariableCode(String currencyVariableCode) { this.currencyVariableCode = currencyVariableCode; }

        /** 读取通用值格式代码。 */
        public String getFormatCode() { return formatCode; }

        /** 设置通用值格式代码。 */
        public void setFormatCode(String formatCode) { this.formatCode = formatCode; }

        /** 读取可选值集合编码。 */
        public String getAllowedValuesVariableCode() { return allowedValuesVariableCode; }

        /** 设置可选值集合编码。 */
        public void setAllowedValuesVariableCode(String allowedValuesVariableCode) { this.allowedValuesVariableCode = allowedValuesVariableCode; }

        /** 读取常量当前值。 */
        public Object getCurrentValue() { return currentValue; }

        /** 设置常量当前值。 */
        public void setCurrentValue(Object currentValue) { this.currentValue = currentValue; }

        /** 读取集合成员数量。 */
        public Long getMemberCount() { return memberCount; }

        /** 设置集合成员数量。 */
        public void setMemberCount(Long memberCount) { this.memberCount = memberCount; }

        /** 读取有界成员预览。 */
        public List<Member> getPreviewMembers() { return previewMembers; }

        /** 设置有界成员预览。 */
        public void setPreviewMembers(List<Member> previewMembers) { this.previewMembers = previewMembers; }

        /** 读取UTC 微秒更新时间。 */
        public String getUpdateTime() { return updateTime; }

        /** 设置UTC 微秒更新时间。 */
        public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
    }

    /** 成员值仅为规范字符串或 Boolean；展示名称可空。 */
    public static class Member {

        /** 类型化值，仅允许字符串或 Boolean。 */
        private Object value;

        /** 成员显示名称；可空。 */
        private String displayName;

        /** JSON 反序列化构造。 */
        public Member() { }

        /** 按完整合同构造。 */
        public Member(Object value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }

        /** 读取类型化值，仅允许字符串或 Boolean。 */
        public Object getValue() { return value; }

        /** 设置类型化值，仅允许字符串或 Boolean。 */
        public void setValue(Object value) { this.value = value; }

        /** 读取成员显示名称。 */
        public String getDisplayName() { return displayName; }

        /** 设置成员显示名称。 */
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }

    /** 成员按整批保存顺序返回，从 1 开始。 */
    public static class OrderedMember {

        /** 类型化值，仅允许字符串或 Boolean。 */
        private Object value;

        /** 成员显示名称；可空。 */
        private String displayName;

        /** 从 1 开始的成员保存顺序。 */
        private int sortOrder;

        /** JSON 反序列化构造。 */
        public OrderedMember() { }

        /** 按完整合同构造。 */
        public OrderedMember(Object value, String displayName, int sortOrder) {
            this.value = value;
            this.displayName = displayName;
            this.sortOrder = sortOrder;
        }

        /** 读取类型化值，仅允许字符串或 Boolean。 */
        public Object getValue() { return value; }

        /** 设置类型化值，仅允许字符串或 Boolean。 */
        public void setValue(Object value) { this.value = value; }

        /** 读取成员显示名称。 */
        public String getDisplayName() { return displayName; }

        /** 设置成员显示名称。 */
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        /** 读取从 1 开始的成员保存顺序。 */
        public int getSortOrder() { return sortOrder; }

        /** 设置从 1 开始的成员保存顺序。 */
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    }

    /** 选项集合的当前版本及最多 100 项预览。 */
    public static class AllowedValues {

        /** 永久唯一变量编码。 */
        private String variableCode;

        /** 运营展示名称。 */
        private String variableName;

        /** 根 CAS 版本，以正整数字符串表示。 */
        private String version;

        /** 集合成员数量；非集合为空。 */
        private long memberCount;

        /** 有界成员预览。 */
        private List<Member> previewMembers;

        /** JSON 反序列化构造。 */
        public AllowedValues() { }

        /** 按完整合同构造。 */
        public AllowedValues(String variableCode, String variableName, String version, long memberCount, List<Member> previewMembers) {
            this.variableCode = variableCode;
            this.variableName = variableName;
            this.version = version;
            this.memberCount = memberCount;
            this.previewMembers = previewMembers;
        }

        /** 读取永久唯一变量编码。 */
        public String getVariableCode() { return variableCode; }

        /** 设置永久唯一变量编码。 */
        public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

        /** 读取运营展示名称。 */
        public String getVariableName() { return variableName; }

        /** 设置运营展示名称。 */
        public void setVariableName(String variableName) { this.variableName = variableName; }

        /** 读取根 CAS 版本，以正整数字符串表示。 */
        public String getVersion() { return version; }

        /** 设置根 CAS 版本，以正整数字符串表示。 */
        public void setVersion(String version) { this.version = version; }

        /** 读取集合成员数量。 */
        public long getMemberCount() { return memberCount; }

        /** 设置集合成员数量。 */
        public void setMemberCount(long memberCount) { this.memberCount = memberCount; }

        /** 读取有界成员预览。 */
        public List<Member> getPreviewMembers() { return previewMembers; }

        /** 设置有界成员预览。 */
        public void setPreviewMembers(List<Member> previewMembers) { this.previewMembers = previewMembers; }
    }

    /** 精确根和版本位置，VARIABLE 消费者没有版本坐标。 */
    public static class VariableReference {

        /** 引用消费者类型。 */
        private String consumerType;

        /** 引用所属根 ID 字符串。 */
        private String rootId;

        /** 引用版本 ID；变量消费者为空。 */
        private String versionId;

        /** 引用业务版本号；变量消费者为空。 */
        private Integer versionNo;

        /** 业务代码。 */
        private String code;

        /** 显示名称。 */
        private String name;

        /** 引用位于 CURRENT 或 HISTORY。 */
        private String scope;

        /** 精确引用位置。 */
        private String location;

        /** 永久唯一变量编码。 */
        private String variableCode;

        /** JSON 反序列化构造。 */
        public VariableReference() { }

        /** 按完整合同构造。 */
        public VariableReference(String consumerType, String rootId, String versionId, Integer versionNo, String code, String name, String scope, String location, String variableCode) {
            this.consumerType = consumerType;
            this.rootId = rootId;
            this.versionId = versionId;
            this.versionNo = versionNo;
            this.code = code;
            this.name = name;
            this.scope = scope;
            this.location = location;
            this.variableCode = variableCode;
        }

        /** 读取引用消费者类型。 */
        public String getConsumerType() { return consumerType; }

        /** 设置引用消费者类型。 */
        public void setConsumerType(String consumerType) { this.consumerType = consumerType; }

        /** 读取引用所属根 ID 字符串。 */
        public String getRootId() { return rootId; }

        /** 设置引用所属根 ID 字符串。 */
        public void setRootId(String rootId) { this.rootId = rootId; }

        /** 读取引用版本 ID。 */
        public String getVersionId() { return versionId; }

        /** 设置引用版本 ID。 */
        public void setVersionId(String versionId) { this.versionId = versionId; }

        /** 读取引用业务版本号。 */
        public Integer getVersionNo() { return versionNo; }

        /** 设置引用业务版本号。 */
        public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

        /** 读取业务代码。 */
        public String getCode() { return code; }

        /** 设置业务代码。 */
        public void setCode(String code) { this.code = code; }

        /** 读取显示名称。 */
        public String getName() { return name; }

        /** 设置显示名称。 */
        public void setName(String name) { this.name = name; }

        /** 读取引用位于 CURRENT 或 HISTORY。 */
        public String getScope() { return scope; }

        /** 设置引用位于 CURRENT 或 HISTORY。 */
        public void setScope(String scope) { this.scope = scope; }

        /** 读取精确引用位置。 */
        public String getLocation() { return location; }

        /** 设置精确引用位置。 */
        public void setLocation(String location) { this.location = location; }

        /** 读取永久唯一变量编码。 */
        public String getVariableCode() { return variableCode; }

        /** 设置永久唯一变量编码。 */
        public void setVariableCode(String variableCode) { this.variableCode = variableCode; }
    }

    /** 当前支持的决策点。 */
    public static class SupportedPoint {

        /** 适用决策点；全局变量可空。 */
        private String pointCode;

        /** 决策点显示名称。 */
        private String pointName;

        /** JSON 反序列化构造。 */
        public SupportedPoint() { }

        /** 按完整合同构造。 */
        public SupportedPoint(String pointCode, String pointName) {
            this.pointCode = pointCode;
            this.pointName = pointName;
        }

        /** 读取适用决策点。 */
        public String getPointCode() { return pointCode; }

        /** 设置适用决策点。 */
        public void setPointCode(String pointCode) { this.pointCode = pointCode; }

        /** 读取决策点显示名称。 */
        public String getPointName() { return pointName; }

        /** 设置决策点显示名称。 */
        public void setPointName(String pointName) { this.pointName = pointName; }
    }

    /** 支持实时累计与名单命中的风控点可选目录；工程登记，不替代变量库与名单库。 */
    public static class FeatureCatalog {

        /** 风控点编码。 */
        private String pointCode;

        /** 金额类累计变量绑定的本点币种输入变量。 */
        private String currencyVariableCode;

        /** 本点可统计的累计事件。 */
        private List<VelocityEventOption> velocityEvents;

        /** 可锚定主体及其可选统计方式与窗口。 */
        private List<VelocityAnchor> velocityAnchors;

        /** 可作为去重对象的主体类型。 */
        private List<String> distinctSubjects;

        /** 名单命中可用的主体类型。 */
        private List<String> listSubjects;

        /** JSON 反序列化构造。 */
        public FeatureCatalog() { }

        /** 按完整合同构造。 */
        public FeatureCatalog(String pointCode, String currencyVariableCode, List<VelocityEventOption> velocityEvents, List<VelocityAnchor> velocityAnchors, List<String> distinctSubjects, List<String> listSubjects) {
            this.pointCode = pointCode;
            this.currencyVariableCode = currencyVariableCode;
            this.velocityEvents = velocityEvents;
            this.velocityAnchors = velocityAnchors;
            this.distinctSubjects = distinctSubjects;
            this.listSubjects = listSubjects;
        }

        /** 读取风控点编码。 */
        public String getPointCode() { return pointCode; }

        /** 设置风控点编码。 */
        public void setPointCode(String pointCode) { this.pointCode = pointCode; }

        /** 读取金额类累计变量绑定的本点币种输入变量。 */
        public String getCurrencyVariableCode() { return currencyVariableCode; }

        /** 设置金额类累计变量绑定的本点币种输入变量。 */
        public void setCurrencyVariableCode(String currencyVariableCode) { this.currencyVariableCode = currencyVariableCode; }

        /** 读取本点可统计的累计事件。 */
        public List<VelocityEventOption> getVelocityEvents() { return velocityEvents; }

        /** 设置本点可统计的累计事件。 */
        public void setVelocityEvents(List<VelocityEventOption> velocityEvents) { this.velocityEvents = velocityEvents; }

        /** 读取可锚定主体及其可选统计方式与窗口。 */
        public List<VelocityAnchor> getVelocityAnchors() { return velocityAnchors; }

        /** 设置可锚定主体及其可选统计方式与窗口。 */
        public void setVelocityAnchors(List<VelocityAnchor> velocityAnchors) { this.velocityAnchors = velocityAnchors; }

        /** 读取可作为去重对象的主体类型。 */
        public List<String> getDistinctSubjects() { return distinctSubjects; }

        /** 设置可作为去重对象的主体类型。 */
        public void setDistinctSubjects(List<String> distinctSubjects) { this.distinctSubjects = distinctSubjects; }

        /** 读取名单命中可用的主体类型。 */
        public List<String> getListSubjects() { return listSubjects; }

        /** 设置名单命中可用的主体类型。 */
        public void setListSubjects(List<String> listSubjects) { this.listSubjects = listSubjects; }
    }
    /** 风控点的一个累计事件。 */
    public static class VelocityEventOption {

        /** 事件编码。 */
        private String eventCode;

        /** 事件显示名称。 */
        private String eventName;

        /** 每次成功评估都写入、可计入本次。 */
        private boolean includeCurrentAllowed;

        /** JSON 反序列化构造。 */
        public VelocityEventOption() { }

        /** 按完整合同构造。 */
        public VelocityEventOption(String eventCode, String eventName, boolean includeCurrentAllowed) {
            this.eventCode = eventCode;
            this.eventName = eventName;
            this.includeCurrentAllowed = includeCurrentAllowed;
        }

        /** 读取事件编码。 */
        public String getEventCode() { return eventCode; }

        /** 设置事件编码。 */
        public void setEventCode(String eventCode) { this.eventCode = eventCode; }

        /** 读取事件显示名称。 */
        public String getEventName() { return eventName; }

        /** 设置事件显示名称。 */
        public void setEventName(String eventName) { this.eventName = eventName; }

        /** 读取每次成功评估都写入、可计入本次。 */
        public boolean getIncludeCurrentAllowed() { return includeCurrentAllowed; }

        /** 设置每次成功评估都写入、可计入本次。 */
        public void setIncludeCurrentAllowed(boolean includeCurrentAllowed) { this.includeCurrentAllowed = includeCurrentAllowed; }
    }
    /** 一个锚定主体可选的统计方式。 */
    public static class VelocityAnchor {

        /** 锚定主体类型。 */
        private String subjectType;

        /** 可选统计方式及窗口。 */
        private List<VelocityMetricOption> metrics;

        /** JSON 反序列化构造。 */
        public VelocityAnchor() { }

        /** 按完整合同构造。 */
        public VelocityAnchor(String subjectType, List<VelocityMetricOption> metrics) {
            this.subjectType = subjectType;
            this.metrics = metrics;
        }

        /** 读取锚定主体类型。 */
        public String getSubjectType() { return subjectType; }

        /** 设置锚定主体类型。 */
        public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

        /** 读取可选统计方式及窗口。 */
        public List<VelocityMetricOption> getMetrics() { return metrics; }

        /** 设置可选统计方式及窗口。 */
        public void setMetrics(List<VelocityMetricOption> metrics) { this.metrics = metrics; }
    }
    /** 锚定主体下一种统计方式的可选窗口。 */
    public static class VelocityMetricOption {

        /** 统计方式。 */
        private String metric;

        /** 可选窗口秒数，升序。 */
        private List<Integer> windowSeconds;

        /** 该统计方式可计入本次。 */
        private boolean includeCurrentAllowed;

        /** JSON 反序列化构造。 */
        public VelocityMetricOption() { }

        /** 按完整合同构造。 */
        public VelocityMetricOption(String metric, List<Integer> windowSeconds, boolean includeCurrentAllowed) {
            this.metric = metric;
            this.windowSeconds = windowSeconds;
            this.includeCurrentAllowed = includeCurrentAllowed;
        }

        /** 读取统计方式。 */
        public String getMetric() { return metric; }

        /** 设置统计方式。 */
        public void setMetric(String metric) { this.metric = metric; }

        /** 读取可选窗口秒数，升序。 */
        public List<Integer> getWindowSeconds() { return windowSeconds; }

        /** 设置可选窗口秒数，升序。 */
        public void setWindowSeconds(List<Integer> windowSeconds) { this.windowSeconds = windowSeconds; }

        /** 读取该统计方式可计入本次。 */
        public boolean getIncludeCurrentAllowed() { return includeCurrentAllowed; }

        /** 设置该统计方式可计入本次。 */
        public void setIncludeCurrentAllowed(boolean includeCurrentAllowed) { this.includeCurrentAllowed = includeCurrentAllowed; }
    }
    /** 工程保留的 NODE_OUTPUT 不开放运营创建。 */
    public static class SourceCapability {

        /** 变量来源类型。 */
        private String sourceType;

        /** 是否允许运营创建。 */
        private boolean createAllowed;

        /** JSON 反序列化构造。 */
        public SourceCapability() { }

        /** 按完整合同构造。 */
        public SourceCapability(String sourceType, boolean createAllowed) {
            this.sourceType = sourceType;
            this.createAllowed = createAllowed;
        }

        /** 读取变量来源类型。 */
        public String getSourceType() { return sourceType; }

        /** 设置变量来源类型。 */
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }

        /** 读取是否允许运营创建。 */
        public boolean getCreateAllowed() { return createAllowed; }

        /** 设置是否允许运营创建。 */
        public void setCreateAllowed(boolean createAllowed) { this.createAllowed = createAllowed; }
    }

    /** 通用格式能力，不按业务变量编码定义。 */
    public static class FormatProfile {

        /** 通用值格式代码。 */
        private String formatCode;

        /** 格式显示标签。 */
        private String label;

        /** 格式说明。 */
        private String help;

        /** JSON 反序列化构造。 */
        public FormatProfile() { }

        /** 按完整合同构造。 */
        public FormatProfile(String formatCode, String label, String help) {
            this.formatCode = formatCode;
            this.label = label;
            this.help = help;
        }

        /** 读取通用值格式代码。 */
        public String getFormatCode() { return formatCode; }

        /** 设置通用值格式代码。 */
        public void setFormatCode(String formatCode) { this.formatCode = formatCode; }

        /** 读取格式显示标签。 */
        public String getLabel() { return label; }

        /** 设置格式显示标签。 */
        public void setLabel(String label) { this.label = label; }

        /** 读取格式说明。 */
        public String getHelp() { return help; }

        /** 设置格式说明。 */
        public void setHelp(String help) { this.help = help; }
    }

    /** 保存容量上限同时受项目 HTTP 传输限制约束。 */
    public static class Limits {

        /** 集合成员数量；非集合为空。 */
        private int memberCount;

        /** 整批成员请求字节上限。 */
        private int memberBodyBytes;

        /** 内联集合容量上限。 */
        private int literalSetCount;

        /** JSON 反序列化构造。 */
        public Limits() { }

        /** 按完整合同构造。 */
        public Limits(int memberCount, int memberBodyBytes, int literalSetCount) {
            this.memberCount = memberCount;
            this.memberBodyBytes = memberBodyBytes;
            this.literalSetCount = literalSetCount;
        }

        /** 读取集合成员数量。 */
        public int getMemberCount() { return memberCount; }

        /** 设置集合成员数量。 */
        public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

        /** 读取整批成员请求字节上限。 */
        public int getMemberBodyBytes() { return memberBodyBytes; }

        /** 设置整批成员请求字节上限。 */
        public void setMemberBodyBytes(int memberBodyBytes) { this.memberBodyBytes = memberBodyBytes; }

        /** 读取内联集合容量上限。 */
        public int getLiteralSetCount() { return literalSetCount; }

        /** 设置内联集合容量上限。 */
        public void setLiteralSetCount(int literalSetCount) { this.literalSetCount = literalSetCount; }
    }

    /** 准确输入或当前规则集节点输出合同；不接受最新版本兜底。 */
    public static class SourceContract {

        /** 工程合同代码。 */
        private String contractCode;

        /** 准确工程合同版本。 */
        private int contractVersion;

        /** 适用决策点；全局变量可空。 */
        private String pointCode;

        /** 变量来源类型。 */
        private String sourceType;

        /** 适用节点类型；非节点输出为空。 */
        private String nodeType;

        /** 合同顶层字段。 */
        private List<SourceField> fields;

        /** JSON 反序列化构造。 */
        public SourceContract() { }

        /** 按完整合同构造。 */
        public SourceContract(String contractCode, int contractVersion, String pointCode, String sourceType, String nodeType, List<SourceField> fields) {
            this.contractCode = contractCode;
            this.contractVersion = contractVersion;
            this.pointCode = pointCode;
            this.sourceType = sourceType;
            this.nodeType = nodeType;
            this.fields = fields;
        }

        /** 读取工程合同代码。 */
        public String getContractCode() { return contractCode; }

        /** 设置工程合同代码。 */
        public void setContractCode(String contractCode) { this.contractCode = contractCode; }

        /** 读取准确工程合同版本。 */
        public int getContractVersion() { return contractVersion; }

        /** 设置准确工程合同版本。 */
        public void setContractVersion(int contractVersion) { this.contractVersion = contractVersion; }

        /** 读取适用决策点。 */
        public String getPointCode() { return pointCode; }

        /** 设置适用决策点。 */
        public void setPointCode(String pointCode) { this.pointCode = pointCode; }

        /** 读取变量来源类型。 */
        public String getSourceType() { return sourceType; }

        /** 设置变量来源类型。 */
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }

        /** 读取适用节点类型。 */
        public String getNodeType() { return nodeType; }

        /** 设置适用节点类型。 */
        public void setNodeType(String nodeType) { this.nodeType = nodeType; }

        /** 读取合同顶层字段。 */
        public List<SourceField> getFields() { return fields; }

        /** 设置合同顶层字段。 */
        public void setFields(List<SourceField> fields) { this.fields = fields; }
    }

    /** 真实来源字段与最大用途；不包含变量编码或运营展示名称。 */
    public static class SourceField {

        /** 准确顶层字段键。 */
        private String fieldKey;

        /** 变量或成员的标量类型。 */
        private String valueType;

        /** 来源字段是否必填。 */
        private boolean required;

        /** 数据分级。 */
        private String dataClassification;

        /** 来源字段允许的最大用途。 */
        private List<String> allowedUsages;

        /** NUMBER 的量纲；其他类型为空。 */
        private String numericUnit;

        /** 金额对应的币种字段；非金额为空。 */
        private String currencyFieldKey;

        /** 批准的主体语义；可空。 */
        private String semanticRole;

        /** JSON 反序列化构造。 */
        public SourceField() { }

        /** 按完整合同构造。 */
        public SourceField(String fieldKey, String valueType, boolean required, String dataClassification, List<String> allowedUsages, String numericUnit, String currencyFieldKey, String semanticRole) {
            this.fieldKey = fieldKey;
            this.valueType = valueType;
            this.required = required;
            this.dataClassification = dataClassification;
            this.allowedUsages = allowedUsages;
            this.numericUnit = numericUnit;
            this.currencyFieldKey = currencyFieldKey;
            this.semanticRole = semanticRole;
        }

        /** 读取准确顶层字段键。 */
        public String getFieldKey() { return fieldKey; }

        /** 设置准确顶层字段键。 */
        public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }

        /** 读取变量或成员的标量类型。 */
        public String getValueType() { return valueType; }

        /** 设置变量或成员的标量类型。 */
        public void setValueType(String valueType) { this.valueType = valueType; }

        /** 读取来源字段是否必填。 */
        public boolean getRequired() { return required; }

        /** 设置来源字段是否必填。 */
        public void setRequired(boolean required) { this.required = required; }

        /** 读取数据分级。 */
        public String getDataClassification() { return dataClassification; }

        /** 设置数据分级。 */
        public void setDataClassification(String dataClassification) { this.dataClassification = dataClassification; }

        /** 读取来源字段允许的最大用途。 */
        public List<String> getAllowedUsages() { return allowedUsages; }

        /** 设置来源字段允许的最大用途。 */
        public void setAllowedUsages(List<String> allowedUsages) { this.allowedUsages = allowedUsages; }

        /** 读取NUMBER 的量纲。 */
        public String getNumericUnit() { return numericUnit; }

        /** 设置NUMBER 的量纲。 */
        public void setNumericUnit(String numericUnit) { this.numericUnit = numericUnit; }

        /** 读取金额对应的币种字段。 */
        public String getCurrencyFieldKey() { return currencyFieldKey; }

        /** 设置金额对应的币种字段。 */
        public void setCurrencyFieldKey(String currencyFieldKey) { this.currencyFieldKey = currencyFieldKey; }

        /** 读取批准的主体语义。 */
        public String getSemanticRole() { return semanticRole; }

        /** 设置批准的主体语义。 */
        public void setSemanticRole(String semanticRole) { this.semanticRole = semanticRole; }
    }

    /** 条件编辑器查询变量选项时使用的准确上下文。 */
    public static class VariableQueryContext {

        /** 适用决策点；全局变量可空。 */
        private String pointCode;

        /** 准确输入合同代码。 */
        private String inputContractCode;

        /** 准确输入合同版本。 */
        private int inputContractVersion;

        /** 变量选择用途。 */
        private String usage;

        /** JSON 反序列化构造。 */
        public VariableQueryContext() { }

        /** 按完整合同构造。 */
        public VariableQueryContext(String pointCode, String inputContractCode, int inputContractVersion, String usage) {
            this.pointCode = pointCode;
            this.inputContractCode = inputContractCode;
            this.inputContractVersion = inputContractVersion;
            this.usage = usage;
        }

        /** 读取适用决策点。 */
        public String getPointCode() { return pointCode; }

        /** 设置适用决策点。 */
        public void setPointCode(String pointCode) { this.pointCode = pointCode; }

        /** 读取准确输入合同代码。 */
        public String getInputContractCode() { return inputContractCode; }

        /** 设置准确输入合同代码。 */
        public void setInputContractCode(String inputContractCode) { this.inputContractCode = inputContractCode; }

        /** 读取准确输入合同版本。 */
        public int getInputContractVersion() { return inputContractVersion; }

        /** 设置准确输入合同版本。 */
        public void setInputContractVersion(int inputContractVersion) { this.inputContractVersion = inputContractVersion; }

        /** 读取变量选择用途。 */
        public String getUsage() { return usage; }

        /** 设置变量选择用途。 */
        public void setUsage(String usage) { this.usage = usage; }
    }

    /** 历史引用当前缺失或损坏时保留原结构并单独标记。 */
    public static class VariableIssue {

        /** 永久唯一变量编码。 */
        private String variableCode;

        /** 业务代码。 */
        private String code;

        /** 可诊断的问题说明。 */
        private String message;

        /** JSON 反序列化构造。 */
        public VariableIssue() { }

        /** 按完整合同构造。 */
        public VariableIssue(String variableCode, String code, String message) {
            this.variableCode = variableCode;
            this.code = code;
            this.message = message;
        }

        /** 读取永久唯一变量编码。 */
        public String getVariableCode() { return variableCode; }

        /** 设置永久唯一变量编码。 */
        public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

        /** 读取业务代码。 */
        public String getCode() { return code; }

        /** 设置业务代码。 */
        public void setCode(String code) { this.code = code; }

        /** 读取可诊断的问题说明。 */
        public String getMessage() { return message; }

        /** 设置可诊断的问题说明。 */
        public void setMessage(String message) { this.message = message; }
    }

    /** 值按根的类型处理，不允许客户端传成员行身份。 */
    public static class MemberRequest {

        /** 类型化值，仅允许字符串或 Boolean。 */
        @NotNull
        private Object value;

        /** 成员显示名称；可空。 */
        private String displayName;

        /** JSON 反序列化构造。 */
        public MemberRequest() { }

        /** 按完整合同构造。 */
        public MemberRequest(Object value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }

        /** 读取类型化值，仅允许字符串或 Boolean。 */
        public Object getValue() { return value; }

        /** 设置类型化值，仅允许字符串或 Boolean。 */
        public void setValue(Object value) { this.value = value; }

        /** 读取成员显示名称。 */
        public String getDisplayName() { return displayName; }

        /** 设置成员显示名称。 */
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}
