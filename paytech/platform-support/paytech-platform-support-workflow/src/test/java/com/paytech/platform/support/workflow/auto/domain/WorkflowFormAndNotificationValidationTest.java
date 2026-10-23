package com.paytech.platform.support.workflow.auto.domain;

import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.callback;
import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.notifyRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.NotificationChannel;
import com.paytech.platform.support.workflow.core.enums.NotificationRecipient;
import com.paytech.platform.support.workflow.core.enums.NotificationRuleType;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.action.FileUploadActionConfig;
import com.paytech.platform.support.workflow.domain.action.FormActionConfig;
import com.paytech.platform.support.workflow.domain.action.ManualConfirmActionConfig;
import com.paytech.platform.support.workflow.domain.action.PageParameter;
import com.paytech.platform.support.workflow.domain.action.PageTaskActionConfig;
import com.paytech.platform.support.workflow.domain.form.EnumOption;
import com.paytech.platform.support.workflow.domain.form.FieldRules;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.form.FormField;
import com.paytech.platform.support.workflow.domain.graph.NotificationRule;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domainservice.validator.ActionConfigValidator;
import com.paytech.platform.support.workflow.domainservice.validator.WorkflowGraphValidator;
import com.paytech.platform.support.workflow.test.builder.PublicationInputBuilder;
import com.paytech.platform.support.workflow.test.builder.WorkflowFixtures;
import com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder;
import com.paytech.platform.support.workflow.test.checker.ProblemChecker;

/**
 * 启动表单字段定义、通知与回调规则（技术方案 3.2、4.2），以及动作模板配置校验（3.3）。
 *
 * @see WorkflowGraphValidator#validate(com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput)
 * @see ActionConfigValidator#validate(com.paytech.platform.support.workflow.domain.action.ActionConfig)
 */
class WorkflowFormAndNotificationValidationTest {

    private final WorkflowGraphValidator validator = new WorkflowGraphValidator();

    private final ActionConfigValidator configValidator = new ActionConfigValidator();

    /** 字段 key、显示名与枚举选项仍校验；长度、范围、文件数量与类型等校验键只按结构接受。 */
    @Test
    void test_startFormRules() {
        FormDefinition form = new FormDefinition(List.of(
            WorkflowFixtures.field("ok", FormFieldType.TEXT),
            WorkflowFixtures.field("ok", FormFieldType.TEXT),
            WorkflowFixtures.field("bad key", FormFieldType.TEXT),
            new FormField("noLabel", " ", FormFieldType.TEXT, false, FieldRules.NONE),
            withRules("longText", FormFieldType.TEXT, new FieldRules(256, null, null, null, List.of(), null, null, List.of())),
            withRules("range", FormFieldType.NUMBER, new FieldRules(null, BigDecimal.TEN, BigDecimal.ONE, null, List.of(),
                null, null, List.of())),
            withRules("noOptions", FormFieldType.ENUM, FieldRules.NONE),
            withRules("dupOptions", FormFieldType.ENUM, new FieldRules(null, null, null, null,
                List.of(new EnumOption("A", "A"), new EnumOption("A", "B")), null, null, List.of())),
            withRules("files", FormFieldType.FILE, new FieldRules(null, null, null, null, List.of(), 3, 1, List.of())),
            withRules("foreign", FormFieldType.BOOLEAN, new FieldRules(10, null, null, null, List.of(), null, null,
                List.of()))));
        List<WorkflowGraphProblem> problems = validator.validate(PublicationInputBuilder.of(
            WorkflowGraphBuilder.template().build()).startForm(form).build());
        ProblemChecker.of(problems)
            .item(WorkflowProblemCode.FIELD_KEY_DUPLICATE, "ok")
            .item(WorkflowProblemCode.FIELD_KEY_INVALID, "bad key")
            .item(WorkflowProblemCode.FIELD_LABEL_MISSING, "noLabel")
            .item(WorkflowProblemCode.FIELD_RULE_INVALID, "noOptions")
            .item(WorkflowProblemCode.FIELD_RULE_INVALID, "dupOptions");
        // 2026-10-22 起 maxLength、min / max、文件数量与类型等校验键忽略，不再报告
        Assertions.assertEquals(List.of(), problems.stream()
            .filter(problem -> List.of("longText", "range", "files", "foreign").contains(problem.itemKey())).toList());
    }

    @Test
    void test_tooManyFields() {
        List<FormField> fields = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            fields.add(WorkflowFixtures.field("f" + index, FormFieldType.TEXT));
        }
        ProblemChecker.of(validator.validate(PublicationInputBuilder.of(WorkflowGraphBuilder.template().build())
            .startForm(new FormDefinition(fields)).build())).exactly(WorkflowProblemCode.TOO_MANY_FIELDS);
        ProblemChecker.of(validator.validate(PublicationInputBuilder.of(WorkflowGraphBuilder.template().build())
            .startForm(new FormDefinition(fields.subList(0, 50))).build())).none();
    }

    @Test
    void test_validNotificationRules() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .task("task", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A)
                .action("confirm", WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A)
                .rule(notifyRule(NotificationTrigger.RETURNED_FOR_REVISION, NotificationRecipient.NODE_EXECUTORS,
                    NotificationRecipient.STARTER))
                .rule(callback(NotificationTrigger.PASSED, "COMMERCIAL_PRODUCT_READY")))
            .end("end").chain("start", "task", "end")
            .settingsRule(notifyRule(NotificationTrigger.COMPLETED, NotificationRecipient.STARTER))
            .settingsRule(callback(NotificationTrigger.TERMINATED, "CHANNEL_LAUNCH_STOPPED"))
            .build();
        ProblemChecker.of(validate(graph)).none();
    }

    @Test
    void test_invalidNotificationRules() {
        NotificationRule noChannel = new NotificationRule(NotificationTrigger.ACTIVATED, NotificationRuleType.NOTIFY,
            List.of(NotificationRecipient.SPECIFIED_USERS), List.of(WorkflowFixtures.DISABLED_ACCOUNT), List.of(), null);
        NotificationRule noRecipient = new NotificationRule(NotificationTrigger.PASSED, NotificationRuleType.NOTIFY,
            List.of(), List.of(WorkflowFixtures.APPROVER_A), List.of(NotificationChannel.EMAIL), "X");
        NotificationRule mixedCallback = new NotificationRule(NotificationTrigger.PASSED, NotificationRuleType.CALLBACK,
            List.of(NotificationRecipient.STARTER), List.of(), List.of(), "bad-code");
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .task("task", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A)
                .rule(noChannel).rule(noRecipient).rule(mixedCallback)
                .rule(notifyRule(NotificationTrigger.COMPLETED, NotificationRecipient.STARTER))
                .rule(notifyRule(NotificationTrigger.VERIFICATION_FAILED, NotificationRecipient.NODE_EXECUTORS)))
            .end("end").chain("start", "task", "end")
            .settingsRule(notifyRule(NotificationTrigger.PASSED, NotificationRecipient.NODE_APPROVERS))
            .build();
        ProblemChecker.of(validate(graph))
            .item(WorkflowProblemCode.NOTIFICATION_CHANNEL_MISSING, "notifications[0]")
            .item(WorkflowProblemCode.NOTIFICATION_RECIPIENT_INACTIVE, "notifications[0]")
            .item(WorkflowProblemCode.NOTIFICATION_RECIPIENT_MISSING, "notifications[1]")
            .item(WorkflowProblemCode.NOTIFICATION_RULE_INVALID, "notifications[1]")
            .item(WorkflowProblemCode.CALLBACK_CODE_INVALID, "notifications[2]")
            .item(WorkflowProblemCode.NOTIFICATION_RULE_INVALID, "notifications[2]")
            .item(WorkflowProblemCode.NOTIFICATION_TRIGGER_INVALID, "notifications[3]")
            .item(WorkflowProblemCode.NOTIFICATION_TRIGGER_INVALID, "notifications[4]")
            .item(WorkflowProblemCode.NOTIFICATION_RECIPIENT_INVALID, "notifications[4]")
            .node(WorkflowProblemCode.NOTIFICATION_TRIGGER_INVALID, null);
    }

    /** 「动作验证未通过」已停用：即使节点有动作，新配置也报告触发事件不合法（枚举值保留以兼容已发布配置）。 */
    @Test
    void test_verificationFailedTriggerRetired() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .task("task", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A)
                .action("confirm", WorkflowFixtures.TPL_CONFIRM, WorkflowFixtures.EXECUTOR_A)
                .rule(notifyRule(NotificationTrigger.VERIFICATION_FAILED, NotificationRecipient.NODE_EXECUTORS)))
            .end("end").chain("start", "task", "end").build();
        ProblemChecker.of(validate(graph)).item(WorkflowProblemCode.NOTIFICATION_TRIGGER_INVALID, "notifications[0]")
            .exactly(WorkflowProblemCode.NOTIFICATION_TRIGGER_INVALID);
    }

    @Test
    void test_notificationOnGatewayRejected() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph()
            .node(new WorkflowNode("start",
                WorkflowNodeType.START, null, null, null, List.of(),
                List.of(), null, List.of(callback(NotificationTrigger.PASSED, "STARTED_CODE"))))
            .task("task", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .end("end").chain("start", "task", "end").build();
        ProblemChecker.of(validate(graph)).exactly(WorkflowProblemCode.NOTIFICATION_NOT_ALLOWED);
    }

    @Test
    void test_actionConfigValidation() {
        ProblemChecker.of(configValidator.validate(new ManualConfirmActionConfig(null, true))).none();
        ProblemChecker.of(configValidator.validate(WorkflowFixtures.templates().get(WorkflowFixtures.TPL_FORM).config()))
            .none();
        ProblemChecker.of(configValidator.validate(new FormActionConfig(FormDefinition.EMPTY)))
            .exactly(WorkflowProblemCode.ACTION_CONFIG_INVALID);
        ProblemChecker.of(configValidator.validate(new FileUploadActionConfig(2, 1, List.of("PDF"), null, false)))
            .none();
        ProblemChecker.of(configValidator.validate(new FileUploadActionConfig(null, null, List.of(), "上传", true)))
            .none();
        for (String route : List.of("https://evil.example/x", "//evil.example", "commercial/agreements")) {
            ProblemChecker.of(configValidator.validate(new PageTaskActionConfig(route, List.of(), null)))
                .exactly(WorkflowProblemCode.ACTION_CONFIG_INVALID);
        }
        ProblemChecker.of(configValidator.validate(new PageTaskActionConfig("/commercial/agreements",
                List.of(new PageParameter("channelCode", "渠道"), new PageParameter("channelCode", "重复")), null)))
            .item(WorkflowProblemCode.ACTION_CONFIG_INVALID, "channelCode");
    }

    /**
     * 带规则的字段。
     *
     * @param key 字段 key
     * @param type 字段类型
     * @param rules 规则
     * @return 字段
     */
    private FormField withRules(String key, FormFieldType type, FieldRules rules) {
        return new FormField(key, key, type, false, rules);
    }

    /**
     * 以默认输入校验。
     *
     * @param graph 流程图
     * @return 问题
     */
    private List<WorkflowGraphProblem> validate(WorkflowGraph graph) {
        return validator.validate(PublicationInputBuilder.of(graph).build());
    }
}
