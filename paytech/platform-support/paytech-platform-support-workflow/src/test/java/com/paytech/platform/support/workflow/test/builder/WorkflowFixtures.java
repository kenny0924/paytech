package com.paytech.platform.support.workflow.test.builder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.action.ActionConfig;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.action.DataQueryOutput;
import com.paytech.platform.support.workflow.domain.validation.DataQueryTemplateFact;
import com.paytech.platform.support.workflow.domain.action.FileUploadActionConfig;
import com.paytech.platform.support.workflow.domain.action.FormActionConfig;
import com.paytech.platform.support.workflow.domain.action.ManualConfirmActionConfig;
import com.paytech.platform.support.workflow.domain.action.PageParameter;
import com.paytech.platform.support.workflow.domain.action.PageTaskActionConfig;
import com.paytech.platform.support.workflow.domain.form.EnumOption;
import com.paytech.platform.support.workflow.domain.form.FieldRules;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.form.FormField;

/**
 * 测试共用的人员、动作模板与启动表单基线。
 */
public final class WorkflowFixtures {

    /** 发起人。 */
    public static final long STARTER = 900000000000000100L;

    /** 审批人甲。 */
    public static final long APPROVER_A = 900000000000000101L;

    /** 审批人乙。 */
    public static final long APPROVER_B = 900000000000000104L;

    /** 执行人甲。 */
    public static final long EXECUTOR_A = 900000000000000102L;

    /** 执行人乙。 */
    public static final long EXECUTOR_B = 900000000000000103L;

    /** 不是 ACTIVE Operations 账号的人员。 */
    public static final long DISABLED_ACCOUNT = 900000000000000199L;

    /** 人工确认模板。 */
    public static final long TPL_CONFIRM = 880000000000000201L;

    /** 表单模板：输出 riskLevel（ENUM）、amount（NUMBER）。 */
    public static final long TPL_FORM = 880000000000000202L;

    /** 页面任务模板：参数 channelCode。 */
    public static final long TPL_PAGE = 880000000000000203L;

    /** 上传文件模板。 */
    public static final long TPL_UPLOAD = 880000000000000204L;

    /** 已停用的人工确认模板。 */
    public static final long TPL_INACTIVE = 880000000000000205L;

    /** 数据查询模板：引用 data-query 模板 CHANNEL_READY，参数 channelCode，输出 level（ENUM LOW / HIGH）。 */
    public static final long TPL_DATA_QUERY = 880000000000000206L;

    /** 数据查询模板引用的 data-query 模板编码。 */
    public static final String QUERY_CODE = "CHANNEL_READY";

    private WorkflowFixtures() {
    }

    /**
     * 全部测试模板，按 ID。
     *
     * @return 模板事实
     */
    public static Map<Long, ActionTemplateReference> templates() {
        return Map.of(
            TPL_CONFIRM, template(TPL_CONFIRM, WorkflowActionType.MANUAL_CONFIRM, ActionTemplateStatus.ACTIVE,
                new ManualConfirmActionConfig("确认已配置", true)),
            TPL_FORM, template(TPL_FORM, WorkflowActionType.FORM, ActionTemplateStatus.ACTIVE,
                new FormActionConfig(new FormDefinition(List.of(
                    enumField("riskLevel", "LOW", "HIGH"),
                    field("amount", FormFieldType.NUMBER))))),
            TPL_PAGE, template(TPL_PAGE, WorkflowActionType.PAGE_TASK, ActionTemplateStatus.ACTIVE,
                new PageTaskActionConfig("/commercial/agreements", List.of(new PageParameter("channelCode", "渠道")),
                    null)),
            TPL_UPLOAD, template(TPL_UPLOAD, WorkflowActionType.FILE_UPLOAD, ActionTemplateStatus.ACTIVE,
                new FileUploadActionConfig(1, 5, List.of("application/pdf"), null, false)),
            TPL_INACTIVE, template(TPL_INACTIVE, WorkflowActionType.MANUAL_CONFIRM, ActionTemplateStatus.INACTIVE,
                new ManualConfirmActionConfig(null, false)),
            TPL_DATA_QUERY, template(TPL_DATA_QUERY, WorkflowActionType.DATA_QUERY, ActionTemplateStatus.ACTIVE,
                new DataQueryActionConfig(QUERY_CODE, List.of("channelCode"),
                    List.of(new DataQueryOutput("level", FormFieldType.ENUM, List.of("LOW", "HIGH"))), null, null)));
    }

    /**
     * 数据查询模板所指 data-query 模板的事实：ACTIVE，必填参数 channelCode，声明 satisfied、missing、out_level，无屏蔽。
     *
     * @return 编码到事实
     */
    public static Map<String, DataQueryTemplateFact> dataQueryTemplates() {
        return Map.of(QUERY_CODE, dataQueryTemplate(true, Map.of("channelCode", true),
            Set.of("satisfied", "missing", "out_level"), Set.of()));
    }

    /**
     * 构造 CHANNEL_READY 的事实。
     *
     * @param active 模板与数据源是否 ACTIVE
     * @param parameters 参数名到是否必填
     * @param declared 声明的列（小写）
     * @param masked 屏蔽的列（小写）
     * @return 事实
     */
    public static DataQueryTemplateFact dataQueryTemplate(boolean active, Map<String, Boolean> parameters,
        Set<String> declared, Set<String> masked) {
        return new DataQueryTemplateFact(QUERY_CODE, active, parameters, declared, masked);
    }

    /**
     * 全部 ACTIVE 的 Operations 账号。
     *
     * @return 账号 ID
     */
    public static Set<Long> activeAccounts() {
        return Set.of(STARTER, APPROVER_A, APPROVER_B, EXECUTOR_A, EXECUTOR_B);
    }

    /**
     * 启动表单基线：每种字段类型各一个。
     *
     * @return 启动表单
     */
    public static FormDefinition startForm() {
        return new FormDefinition(List.of(
            field("channelCode", FormFieldType.TEXT),
            field("note", FormFieldType.LONG_TEXT),
            enumField("paymentMethod", "CARD", "APPLE_PAY"),
            new FormField("amount", "金额", FormFieldType.NUMBER, false,
                new FieldRules(null, BigDecimal.ZERO, new BigDecimal("1000000"), 2, List.of(), null, null, List.of())),
            field("urgent", FormFieldType.BOOLEAN),
            field("launchDate", FormFieldType.DATE),
            field("contractFiles", FormFieldType.FILE)));
    }

    /**
     * 无额外规则的字段。
     *
     * @param key 字段 key
     * @param type 字段类型
     * @return 字段
     */
    public static FormField field(String key, FormFieldType type) {
        return new FormField(key, key + " 字段", type, false, FieldRules.NONE);
    }

    /**
     * 枚举字段。
     *
     * @param key 字段 key
     * @param values 选项值
     * @return 字段
     */
    public static FormField enumField(String key, String... values) {
        List<EnumOption> options = Arrays.stream(values).map(value -> new EnumOption(value, value)).toList();
        return new FormField(key, key + " 字段", FormFieldType.ENUM, true,
            new FieldRules(null, null, null, null, options, null, null, List.of()));
    }

    /**
     * 构造模板事实。
     *
     * @param id 模板 ID
     * @param type 动作类型
     * @param status 模板状态
     * @param config 配置
     * @return 模板事实
     */
    private static ActionTemplateReference template(long id, WorkflowActionType type, ActionTemplateStatus status,
        ActionConfig config) {
        return new ActionTemplateReference(id, 1, type, status, config);
    }
}
