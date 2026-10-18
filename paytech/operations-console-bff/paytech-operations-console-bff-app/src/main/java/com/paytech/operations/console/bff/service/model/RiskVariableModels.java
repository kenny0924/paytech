package com.paytech.operations.console.bff.service.model;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 统一变量管理的轻量 HTTP 契约；不暴露 Mapper 或 DataObject。 */
public final class RiskVariableModels {
    /** 仅提供合同命名空间。 */
    private RiskVariableModels() { }

    /** 目录当前值的有限投影；输入和节点输出的 currentValue 恒为 null。 */
    public record VariableSummary(
        String variableCode,
        String variableName,
        String sourceType,
        String valueType,
        String pointCode,
        List<String> usages,
        boolean systemManaged,
        String status,
        String version,
        String numericUnit,
        String currencyCode,
        String currencyVariableCode,
        String formatCode,
        String allowedValuesVariableCode,
        Object currentValue,
        Long memberCount,
        List<Member> previewMembers,
        String updateTime) { }

    /** 当前详情包含来源元数据，不保存输入运行值或共享值历史。 */
    public record VariableDetailResponseBody(
        String variableCode,
        String variableName,
        String sourceType,
        String valueType,
        String pointCode,
        List<String> usages,
        boolean systemManaged,
        String status,
        String version,
        String numericUnit,
        String currencyCode,
        String currencyVariableCode,
        String formatCode,
        String allowedValuesVariableCode,
        Object currentValue,
        Long memberCount,
        List<Member> previewMembers,
        String updateTime,
        String description,
        String sourceContractCode,
        Integer sourceContractVersion,
        String sourceField,
        String createBy,
        String updateBy,
        String createTime,
        String valuesAsOf,
        AllowedValues allowedValues) { }

    /** 普通查询返回分页；codes 批取时 page/size/total 为空且返回 missingCodes。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VariableListResponseBody(
        List<VariableSummary> items,
        Integer page,
        Integer size,
        Long total,
        List<String> missingCodes,
        String valuesAsOf) { }

    /** 成员值仅为规范字符串或 Boolean；展示名称可空。 */
    public record Member(
        Object value,
        String displayName) { }

    /** 成员按整批保存顺序返回，从 1 开始。 */
    public record OrderedMember(
        Object value,
        String displayName,
        int sortOrder) { }

    /** 选项集合的当前版本及最多 100 项预览。 */
    public record AllowedValues(
        String variableCode,
        String variableName,
        String version,
        long memberCount,
        List<Member> previewMembers) { }

    /** 成员与根版本属于同一读取快照。 */
    public record VariableMembersResponseBody(
        String variableCode,
        String version,
        List<OrderedMember> items,
        int page,
        int size,
        long total,
        String valuesAsOf) { }

    /** 整批替换成功结果；失败不保留部分成员。 */
    public record ReplaceMembersResponseBody(
        String variableCode,
        String version,
        int memberCount,
        String updateTime) { }

    /** 无引用时保留 DELETED 墓碑及永久编码。 */
    public record DeleteVariableResponseBody(
        String variableCode,
        String status,
        String version) { }

    /** 精确根和版本位置，VARIABLE 消费者没有版本坐标。 */
    public record VariableReference(
        String consumerType,
        String rootId,
        String versionId,
        Integer versionNo,
        String code,
        String name,
        String scope,
        String location,
        String variableCode) { }

    /** 游标扫描可能返回空页且 hasMore=true；不提供虚假总数。 */
    public record VariableReferencesResponseBody(
        List<VariableReference> items,
        String nextCursor,
        boolean hasMore,
        int scannedRows) { }

    /** 工程合同只用于验证映射能力，变量候选必须查询变量库。 */
    public record VariableManagementContextResponseBody(
        List<SupportedPoint> supportedPoints,
        List<SourceCapability> sources,
        List<String> valueTypes,
        List<FormatProfile> formatProfiles,
        List<String> numericUnits,
        Limits limits,
        List<SourceContract> contracts,
        List<FeatureCatalog> featureCatalogs) { }

    /** 支持实时累计与名单命中的风控点可选目录；由 Risk 工程登记，BFF 原样投影。 */
    public record FeatureCatalog(
        String pointCode,
        String currencyVariableCode,
        List<VelocityEventOption> velocityEvents,
        List<VelocityAnchor> velocityAnchors,
        List<String> distinctSubjects,
        List<String> listSubjects) { }

    /** 风控点的一个累计事件；includeCurrentAllowed 表示可计入本次。 */
    public record VelocityEventOption(
        String eventCode,
        String eventName,
        boolean includeCurrentAllowed) { }

    /** 一个锚定主体可选的统计方式。 */
    public record VelocityAnchor(
        String subjectType,
        List<VelocityMetricOption> metrics) { }

    /** 锚定主体下一种统计方式的可选窗口秒数。 */
    public record VelocityMetricOption(
        String metric,
        List<Integer> windowSeconds,
        boolean includeCurrentAllowed) { }

    /** 当前支持的决策点。 */
    public record SupportedPoint(
        String pointCode,
        String pointName) { }

    /** 工程保留的 NODE_OUTPUT 不开放运营创建。 */
    public record SourceCapability(
        String sourceType,
        boolean createAllowed) { }

    /** 通用格式能力，不按业务变量编码定义。 */
    public record FormatProfile(
        String formatCode,
        String label,
        String help) { }

    /** 保存容量上限同时受项目 HTTP 传输限制约束。 */
    public record Limits(
        int memberCount,
        int memberBodyBytes,
        int literalSetCount) { }

    /** 准确输入或当前规则集节点输出合同；不接受最新版本兜底。 */
    public record SourceContract(
        String contractCode,
        int contractVersion,
        String pointCode,
        String sourceType,
        String nodeType,
        List<SourceField> fields) { }

    /** 真实来源字段与最大用途；不包含变量编码或运营展示名称。 */
    public record SourceField(
        String fieldKey,
        String valueType,
        boolean required,
        String dataClassification,
        List<String> allowedUsages,
        String numericUnit,
        String currencyFieldKey,
        String semanticRole) { }

    /** 条件编辑器查询变量选项时使用的准确上下文。 */
    public record VariableQueryContext(
        String pointCode,
        String inputContractCode,
        int inputContractVersion,
        String usage) { }

    /** 历史引用当前缺失或损坏时保留原结构并单独标记。 */
    public record VariableIssue(
        String variableCode,
        String code,
        String message) { }

    /** 创建后结构固定；所有不适用字段显式 null，系统身份和审计字段由服务端提供。 */
    public record CreateVariableRequestBody(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String variableCode,
        @NotBlank String variableName,
        String description,
        @NotBlank String sourceType,
        @NotBlank String valueType,
        String pointCode,
        String sourceContractCode,
        @Positive Integer sourceContractVersion,
        String sourceField,
        @NotNull @Size(min = 1, max = 3) List<@NotBlank String> usages,
        @NotBlank String formatCode,
        String numericUnit,
        String currencyCode,
        String currencyVariableCode,
        String allowedValuesVariableCode,
        Object currentValue,
        @NotBlank String status,
        @Valid VelocityParameters velocity,
        @Valid ListParameters list) {
        /** 字段、常量、集合来源不带累计与名单参数。 */
        public CreateVariableRequestBody(String variableCode, String variableName, String description, String sourceType,
            String valueType, String pointCode, String sourceContractCode, Integer sourceContractVersion, String sourceField,
            List<String> usages, String formatCode, String numericUnit, String currencyCode, String currencyVariableCode,
            String allowedValuesVariableCode, Object currentValue, String status) {
            this(variableCode, variableName, description, sourceType, valueType, pointCode, sourceContractCode,
                sourceContractVersion, sourceField, usages, formatCode, numericUnit, currencyCode, currencyVariableCode,
                allowedValuesVariableCode, currentValue, status, null, null);
        }
    }

    /**
     * 实时累计参数：仅 sourceType=VELOCITY 时填写，创建后不可修改。
     * <li>事件、主体、算法、窗口的可用组合由 Risk 校验；distinctSubjectType 只用于 DISTINCT_COUNT，其余为 null。</li>
     */
    public record VelocityParameters(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String eventCode,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String anchorSubjectType,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String metric,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String distinctSubjectType,
        @NotNull @Positive Integer windowSeconds,
        @NotNull Boolean includeCurrent) { }

    /** 名单命中参数：仅 sourceType=LIST 时填写，创建后不可修改。 */
    public record ListParameters(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String listCode,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String subjectType) { }

    /** 使用期望根版本维护当前值，禁止提交结构字段。 */
    public record UpdateVariableRequestBody(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedVersion,
        @NotBlank String variableName,
        String description,
        Object currentValue,
        @NotBlank String status) { }

    /** 原子替换集合；空数组清空，null 或重复规范值使整批失败。 */
    public record ReplaceMembersRequestBody(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedVersion,
        @NotNull @Size(max = 1000) List<@NotNull @Valid MemberRequest> members) { }

    /** 值按根的类型处理，不允许客户端传成员行身份。 */
    public record MemberRequest(@NotNull Object value, String displayName) { }
}
