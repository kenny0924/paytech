package com.paytech.platform.support.workflow.auto.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.ConditionOperator;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.InputMappingSource;
import com.paytech.platform.support.workflow.core.enums.NotificationRuleType;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.domain.action.DataQueryActionConfig;
import com.paytech.platform.support.workflow.domain.action.FileUploadActionConfig;
import com.paytech.platform.support.workflow.domain.action.FormActionConfig;
import com.paytech.platform.support.workflow.domain.action.ManualConfirmActionConfig;
import com.paytech.platform.support.workflow.domain.action.PageTaskActionConfig;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.graph.ConditionValue;
import com.paytech.platform.support.workflow.domain.graph.InputMapping;
import com.paytech.platform.support.workflow.domain.graph.NodeAction;
import com.paytech.platform.support.workflow.domain.graph.WorkflowEdge;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput;
import com.paytech.platform.support.workflow.domainservice.convert.ActionConfigJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.FormDefinitionJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowJsonFieldException;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowGraphJsonReader;
import com.paytech.platform.support.workflow.domainservice.validator.WorkflowGraphValidator;
import com.paytech.platform.support.workflow.test.builder.WorkflowFixtures;
import com.paytech.platform.support.workflow.test.checker.ProblemChecker;

/**
 * graph_json、start_form_json 与动作模板 config_json 的严格解析（技术方案 3.1–3.3）。
 *
 * @see WorkflowGraphJsonReader#read(String)
 * @see FormDefinitionJsonReader#read(String)
 * @see ActionConfigJsonReader#read(WorkflowActionType, String)
 */
class WorkflowJsonReaderTest {

    private static final String GRAPH = """
        {
          "nodes": [
            {"key": "start", "type": "START", "name": "开始", "position": {"x": 0, "y": 0}},
            {"key": "approve", "type": "TASK", "name": "立项审批", "position": {"x": 0, "y": 120},
             "approval": {"mode": "ANY", "approverIds": ["900000000000000101"]}, "actions": []},
            {"key": "commerce", "type": "TASK", "name": "商务：配置产品与价目",
             "actions": [{"key": "commerceProduct", "templateId": "880000000000000203", "required": true,
                          "executorId": "900000000000000102",
                          "inputs": {"channelCode": {"source": "VARIABLE", "value": "start.channelCode"}}}],
             "notifications": [{"trigger": "PASSED", "type": "CALLBACK", "callbackCode": "COMMERCIAL_PRODUCT_READY"}]},
            {"key": "applePay", "type": "EXCLUSIVE", "name": "含 Apple Pay？",
             "conditions": [{"edgeKey": "e7", "all": [
                {"variable": "start.paymentMethod", "operator": "EQ", "value": "APPLE_PAY"},
                {"variable": "start.amount", "operator": "IN", "value": [10, "20.5"]}]}],
             "defaultEdgeKey": "e8"},
            {"key": "end", "type": "END"},
            {"key": "end2", "type": "END"}
          ],
          "edges": [{"key": "e1", "from": "start", "to": "approve"}, {"key": "e2", "from": "approve", "to": "commerce"},
                    {"key": "e3", "from": "commerce", "to": "applePay"}, {"key": "e7", "from": "applePay", "to": "end2"},
                    {"key": "e8", "from": "applePay", "to": "end"}],
          "settings": {"notifications": [{"trigger": "COMPLETED", "type": "NOTIFY", "recipients": ["STARTER"],
                                          "channels": ["INBOX", "EMAIL"]}]}
        }
        """;

    private final WorkflowGraphJsonReader graphReader = new WorkflowGraphJsonReader();

    private final FormDefinitionJsonReader formReader = new FormDefinitionJsonReader();

    private final ActionConfigJsonReader configReader = new ActionConfigJsonReader();

    @Test
    void test_readTechniqueExample() {
        WorkflowGraph graph = graphReader.read(GRAPH);
        Assertions.assertEquals(6, graph.nodes().size());
        WorkflowNode approve = graph.node("approve").orElseThrow();
        Assertions.assertEquals(ApprovalMode.ANY, approve.approval().mode());
        Assertions.assertEquals(List.of(WorkflowFixtures.APPROVER_A), approve.approval().approverIds());
        NodeAction action = graph.node("commerce").orElseThrow().actions().get(0);
        Assertions.assertEquals(WorkflowFixtures.TPL_PAGE, action.templateId());
        Assertions.assertTrue(action.required());
        Assertions.assertEquals(Map.of("channelCode", new InputMapping(InputMappingSource.VARIABLE, "start.channelCode")),
            action.inputs());
        WorkflowNode gate = graph.node("applePay").orElseThrow();
        Assertions.assertEquals(WorkflowNodeType.EXCLUSIVE, gate.type());
        Assertions.assertEquals("e8", gate.defaultEdgeKey());
        Assertions.assertEquals(ConditionOperator.IN, gate.conditions().get(0).all().get(1).operator());
        Assertions.assertEquals(ConditionValue.listOf(List.of("10", "20.5")), gate.conditions().get(0).all().get(1).value());
        Assertions.assertEquals(NotificationRuleType.CALLBACK,
            graph.node("commerce").orElseThrow().notifications().get(0).type());
        Assertions.assertEquals(NotificationTrigger.COMPLETED, graph.settings().notifications().get(0).trigger());
        Assertions.assertEquals(List.of("e7", "e8"), graph.outgoing("applePay").stream().map(WorkflowEdge::key).toList());
        Assertions.assertEquals(Set.of(WorkflowFixtures.TPL_PAGE), graph.referencedTemplateIds());
        Assertions.assertEquals(Set.of(WorkflowFixtures.APPROVER_A, WorkflowFixtures.EXECUTOR_A),
            graph.referencedAccountIds());
    }

    @Test
    void test_parsedExamplePassesPublication() {
        WorkflowGraph graph = graphReader.read(GRAPH);
        WorkflowPublicationInput input = new WorkflowPublicationInput(graph,
            GRAPH.getBytes(StandardCharsets.UTF_8).length, WorkflowFixtures.startForm(), WorkflowFixtures.templates(),
            WorkflowFixtures.activeAccounts());
        ProblemChecker.of(new WorkflowGraphValidator().validate(input)).none();
    }

    @Test
    void test_invalidGraphJson() {
        for (String json : List.of(
            "",
            "[]",
            "{\"nodes\": []}",
            "{\"nodes\": [], \"edges\": [], \"extra\": 1}",
            "{\"nodes\": [{\"key\": \"a\", \"type\": \"GATEWAY\"}], \"edges\": []}",
            "{\"nodes\": [{\"key\": \"a\"}], \"edges\": []}",
            "{\"nodes\": [{\"key\": \"a\", \"type\": \"TASK\", \"color\": \"red\"}], \"edges\": []}",
            "{\"nodes\": [{\"key\": \"a\", \"type\": \"TASK\", \"approval\": {\"mode\": \"ANY\", \"approverIds\": [101]}}],"
                + " \"edges\": []}",
            "{\"nodes\": [{\"key\": \"a\", \"type\": \"TASK\", \"actions\": [{\"key\": \"x\", \"templateId\": \"abc\"}]}],"
                + " \"edges\": []}",
            "{\"nodes\": [{\"key\": \"g\", \"type\": \"EXCLUSIVE\", \"conditions\": [{\"edgeKey\": \"e\", \"all\":"
                + " [{\"variable\": \"start.a\", \"operator\": \"LIKE\", \"value\": \"x\"}]}]}], \"edges\": []}",
            "{\"nodes\": [{\"key\": \"g\", \"type\": \"EXCLUSIVE\", \"conditions\": [{\"edgeKey\": \"e\", \"all\":"
                + " [{\"variable\": \"start.a\", \"operator\": \"EQ\", \"value\": {\"x\": 1}}]}]}], \"edges\": []}",
            "{\"nodes\": [], \"edges\": [{\"key\": \"e\", \"from\": 1, \"to\": \"b\"}]}",
            "{\"nodes\": [], \"edges\": [], \"settings\": {\"notifications\": [{\"trigger\": \"PASSED\"}]}}")) {
            Assertions.assertThrows(InvalidParameterException.class, () -> graphReader.read(json), json);
        }
    }

    @Test
    void test_readStartForm() {
        FormDefinition form = formReader.read("""
            {"fields": [
              {"key": "channelCode", "label": "渠道编码", "type": "TEXT", "required": true, "rules": {"maxLength": 64}},
              {"key": "paymentMethod", "label": "支付方式", "type": "ENUM", "required": true,
               "rules": {"options": [{"value": "CARD", "label": "卡"}, {"value": "APPLE_PAY", "label": "Apple Pay"}]}},
              {"key": "amount", "label": "金额", "type": "NUMBER", "rules": {"min": "0", "max": 1000, "scale": 2}},
              {"key": "contractFiles", "label": "渠道合同", "type": "FILE", "required": true,
               "rules": {"minFiles": 1, "maxFiles": 5, "mimeTypes": ["application/pdf"]}}
            ]}
            """);
        Assertions.assertEquals(4, form.fields().size());
        Assertions.assertEquals(64, form.fields().get(0).rules().maxLength());
        Assertions.assertEquals(2, form.fields().get(1).rules().options().size());
        Assertions.assertEquals(0, new BigDecimal("1000").compareTo(form.fields().get(2).rules().max()));
        Assertions.assertEquals(FormFieldType.FILE, form.fields().get(3).type());
        Assertions.assertFalse(form.fields().get(2).required());
        for (String json : List.of("{\"fields\": [{\"key\": \"a\", \"type\": \"MONEY\"}]}",
            "{\"fields\": [{\"key\": \"a\", \"type\": \"TEXT\", \"rules\": {\"maxLength\": \"64\"}}]}",
            "{\"fields\": [{\"key\": \"a\", \"type\": \"NUMBER\", \"rules\": {\"min\": \"1e3\"}}]}",
            "{\"fields\": [], \"title\": \"x\"}")) {
            Assertions.assertThrows(InvalidParameterException.class, () -> formReader.read(json), json);
        }
    }

    @Test
    void test_readActionConfigs() {
        Assertions.assertInstanceOf(FormActionConfig.class, configReader.read(WorkflowActionType.FORM,
            "{\"fields\": [{\"key\": \"riskLevel\", \"label\": \"风险\", \"type\": \"TEXT\"}]}"));
        FileUploadActionConfig upload = (FileUploadActionConfig) configReader.read(WorkflowActionType.FILE_UPLOAD,
            "{\"minFiles\": 1, \"maxFiles\": 5, \"mimeTypes\": [\"application/pdf\"], \"instruction\": \"上传合同\"}");
        Assertions.assertEquals(5, upload.maxFiles());
        PageTaskActionConfig page = (PageTaskActionConfig) configReader.read(WorkflowActionType.PAGE_TASK,
            "{\"pageRoute\": \"/commercial/agreements\", \"parameters\": [{\"key\": \"channelCode\", \"label\": \"渠道\"}],"
                + " \"instruction\": \"\"}");
        Assertions.assertEquals(Set.of("channelCode"), page.inputKeys());
        ManualConfirmActionConfig confirm = (ManualConfirmActionConfig) configReader.read(
            WorkflowActionType.MANUAL_CONFIRM, "{\"instruction\": \"\", \"noteRequired\": true}");
        Assertions.assertTrue(confirm.noteRequired());
        DataQueryActionConfig query = (DataQueryActionConfig) configReader.read(WorkflowActionType.DATA_QUERY,
            "{\"queryCode\": \"CHANNEL_READY\", \"parameters\": [], \"passRule\": {}, \"evidenceColumns\": []}");
        Assertions.assertEquals("CHANNEL_READY", query.queryCode());
        Assertions.assertTrue(query.outputs("q").isEmpty());
        Assertions.assertThrows(InvalidParameterException.class,
            () -> configReader.read(WorkflowActionType.MANUAL_CONFIRM, "{\"pageRoute\": \"/x\"}"));
        Assertions.assertThrows(InvalidParameterException.class,
            () -> configReader.read(WorkflowActionType.FILE_UPLOAD, "{\"maxFiles\": 1.5}"));
        // 结构错误带出错位置，原因不含提交的原值。
        WorkflowJsonFieldException unknownType = Assertions.assertThrows(WorkflowJsonFieldException.class,
            () -> configReader.read(WorkflowActionType.FORM, "{\"fields\": [{\"key\": \"a\", \"label\": \"A\","
                + " \"type\": \"TEXT\"}, {\"key\": \"b\", \"label\": \"B\", \"type\": \"NOPE\"}]}"));
        Assertions.assertEquals("config_json.fields[1].type", unknownType.getPath());
        Assertions.assertFalse(unknownType.getReason().contains("NOPE"), unknownType.getReason());
        WorkflowJsonFieldException unknownProperty = Assertions.assertThrows(WorkflowJsonFieldException.class,
            () -> configReader.read(WorkflowActionType.MANUAL_CONFIRM, "{\"pageRoute\": \"/x\"}"));
        Assertions.assertEquals("config_json.pageRoute", unknownProperty.getPath());
    }
}
