package com.paytech.operations.console.bff.service.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;
import com.paytech.operations.console.bff.validation.DictionaryText;
import java.util.List;

/** 原因与主体标签的当前值合同；不承诺历史写入回放。 */
public final class RiskDictionaryModels {
    /** 仅作为模型或转换方法的命名空间，不允许创建实例。 */
    private RiskDictionaryModels() { }
    /**
     * 原因当前值；名称与默认文案不会回写历史规则。
     * @param reasonCode 永久唯一的原因编码，停用后不可复用
     * @param description 内部说明；响应键始终存在，允许为 null
     * @param defaultMessage 人工选择时可带入的默认内部文案，不是外部客户错误消息
     * @param status 当前目录资格，ACTIVE 或 DISABLED
     * @param version 当前字典修订号，以正 BIGINT 十进制字符串传输
     * @param createdBy 创建人的 Account ID，正 BIGINT 字符串
     * @param updatedBy 最后更新人的 Account ID，正 BIGINT 字符串
     * @param createTime 创建时间，UTC 且固定六位小数
     * @param updateTime 最后更新时间，UTC 且固定六位小数
     */
    public record RejectionReasonResponseBody(String reasonCode, String name, String description,
        String defaultMessage, String status, String version, String createdBy, String updatedBy, String createTime, String updateTime) { }

    /**
     * 标签当前值；标签含义由人工维护，不声明适用主体限制。
     * @param tagCode 永久唯一的标签编码，停用后不可复用
     * @param description 标签含义说明；响应键始终存在，允许为 null
     * @param status 当前目录资格，ACTIVE 或 DISABLED
     * @param version 当前字典修订号，以正 BIGINT 十进制字符串传输
     * @param createdBy 创建人的 Account ID，正 BIGINT 字符串
     * @param updatedBy 最后更新人的 Account ID，正 BIGINT 字符串
     * @param createTime 创建时间，UTC 且固定六位小数
     * @param updateTime 最后更新时间，UTC 且固定六位小数
     */
    public record SubjectTagResponseBody(String tagCode, String name, String description, String status, String version, String createdBy, String updatedBy, String createTime, String updateTime) { }

    /**
     * 仅表示本次字典事务的结果，不提供历史回执或重放。
     * @param dictionaryType REJECTION_REASON 或 SUBJECT_TAG
     * @param code 本次写入的永久字典编码
     * @param version 创建时为 1；更新或启停时递增一版，以正 BIGINT 字符串传输
     * @param status 本次提交后的 ACTIVE 或 DISABLED 状态
     * @param changedBy 本次可信操作者的 Account ID，正 BIGINT 字符串
     * @param changedTime 本次变更时间，UTC 且固定六位小数
     */
    public record DictionaryWriteResponseBody(String dictionaryType, String code, String version,
        String status, String changedBy, String changedTime) { }

    /**
     * 精确保存项的引用，名称取自保存版本，活动环境独立于当前配置指针。
     * @param ruleSetId 整体规则根 ID，正 BIGINT 字符串
     * @param ruleSetVersionId 精确历史版本 ID，正 BIGINT 字符串
     * @param versionNo 规则集内递增的业务版本号，不是数据库 ID
     * @param referenceKind REJECTION_REASON 或 SUBJECT_TAG
     * @param actionSequenceNo 标签所在附加动作的正整数序号；原因引用为 null
     * @param targetBindingCode 标签动作保存的主体绑定编码；原因引用为 null
     * @param ruleSetStatus 整体根状态，CONFIGURABLE 或 DELETED
     * @param isCurrentConfiguration 是否等于根当前版本指针；删除根也可能为 true
     * @param activeEnvironments 该精确版本的活动发布环境，排序去重；删除根必须为空
     */
    public record DictionaryReference(String ruleSetId, String pointCode, String ruleSetCode,
        String ruleSetName, String ruleSetVersionId, int versionNo, String ruleCode, String ruleName,
        String referenceKind, Integer actionSequenceNo, String targetBindingCode, String ruleSetStatus,
        boolean isCurrentConfiguration, List<String> activeEnvironments) { }

    /**
     * 创建并启用原因；编码永久唯一，不能通过重复提交恢复历史结果。
     * @param reasonCode 1 至 64 位大写字母、数字或下划线，以字母开头
     * @param name 1 至 100 个 Unicode 码点，不允许首尾空白
     * @param description 内部说明，键必填但可为 null；空字符串归一为 null，最多 500 个码点
     * @param defaultMessage 非空默认内部文案，最多 500 个码点，可含换行
     */
    public record CreateRejectionReasonRequestBody(
        @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String reasonCode,
        @DictionaryText(max = 100, trimmed = true) String name,
        @DictionaryText(max = 500, nullable = true, multiline = true) String description,
        @DictionaryText(max = 500, multiline = true) String defaultMessage) { }

    /**
     * 完整更新原因内容，不修改编码或状态。
     * @param expectedVersion 调用方最近读取的正 BIGINT 修订号，必须与当前值相同
     * @param name 1 至 100 个 Unicode 码点，不允许首尾空白
     * @param description 内部说明，键必填但可为 null；空字符串归一为 null，最多 500 个码点
     * @param defaultMessage 非空默认内部文案，最多 500 个码点；不改写已有规则
     */
    public record UpdateRejectionReasonRequestBody(
        @NotNull @DecimalMax("9223372036854775807") @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedVersion,
        @DictionaryText(max = 100, trimmed = true) String name,
        @DictionaryText(max = 500, nullable = true, multiline = true) String description,
        @DictionaryText(max = 500, multiline = true) String defaultMessage) { }

    /**
     * 创建并启用标签；含义由人工维护，不建立主体类型约束。
     * @param tagCode 1 至 64 位大写字母、数字或下划线，以字母开头
     * @param name 1 至 100 个 Unicode 码点，不允许首尾空白
     * @param description 含义说明，键必填但可为 null；空字符串归一为 null，最多 500 个码点
     */
    public record CreateSubjectTagRequestBody(
        @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String tagCode,
        @DictionaryText(max = 100, trimmed = true) String name,
        @DictionaryText(max = 500, nullable = true, multiline = true) String description) { }

    /**
     * 完整更新标签名称与说明，不修改编码或状态。
     * @param expectedVersion 调用方最近读取的正 BIGINT 修订号，必须与当前值相同
     * @param name 1 至 100 个 Unicode 码点，不允许首尾空白
     * @param description 含义说明，键必填但可为 null；空字符串归一为 null，最多 500 个码点
     */
    public record UpdateSubjectTagRequestBody(
        @NotNull @DecimalMax("9223372036854775807") @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedVersion,
        @DictionaryText(max = 100, trimmed = true) String name,
        @DictionaryText(max = 500, nullable = true, multiline = true) String description) { }

    /**
     * 独立启停；允许停用已有引用的代码，旧规则和发布事实保持原状。
     * @param expectedVersion 调用方最近读取的正 BIGINT 修订号，必须与当前值相同
     * @param status 目标 ACTIVE 或 DISABLED；每次成功提交均递增版本
     */
    public record ChangeDictionaryStatusRequestBody(
        @NotNull @DecimalMax("9223372036854775807") @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedVersion,
        @NotNull @Pattern(regexp = "ACTIVE|DISABLED") String status) { }
    /** 原因专用反查；保留全部规则字段，流程来源的规则专属字段显式为 null。 */
    public record RejectionReasonReference(String ruleSetId, String pointCode, String ruleSetCode,
        String ruleSetName, String ruleSetVersionId, Integer versionNo, String ruleCode, String ruleName,
        String referenceKind, Integer actionSequenceNo, String targetBindingCode, String ruleSetStatus,
        boolean isCurrentConfiguration, List<String> activeEnvironments, String sourceType, String flowId,
        String flowCode, String flowName, String flowVersionId, Integer flowVersionNo, String nodeKey,
        String nodeName, String flowStatus) { }
}
