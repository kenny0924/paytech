package com.paytech.platform.support.test.base;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.platform.support.api.workflow.request.StartWorkflowInstanceRequestBody;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.DataQueryTestMapper;
import com.paytech.platform.support.test.dataquery.DataQueryFixture;
import com.paytech.platform.support.test.dataquery.DataQueryMocker;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.service.handler.WorkflowDataQueryCheckHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * DATA_QUERY 工作流入口测试的公共准备（REQ-05）：被查库就绪检查、一个只读数据源，以及按单行约定的查询模板、
 * DATA_QUERY 动作模板与按编码发起的调用。
 *
 * <p>被查库 mor_data_query_fixture 的 t_client_fixture：1001 / 1003 为 ORGANIZATION，1002 为 INDIVIDUAL，其余不存在。</p>
 */
public abstract class WorkflowDataQueryTestBase extends WorkflowApiTestBase {

    /** 按 Client 判断存在与类型的查询：存在即满足，输出 client_type、client_no，不存在时原因码 CLIENT_NOT_FOUND。 */
    protected static final String CLIENT_BODY = "SELECT IF(COUNT(*) > 0, 1, 0) AS satisfied,"
        + " MAX(client_type) AS out_client_type, MAX(client_no) AS out_client_no,"
        + " IF(COUNT(*) > 0, NULL, 'CLIENT_NOT_FOUND') AS missing FROM t_client_fixture WHERE client_id = #{clientId}";

    /** 必填参数 clientId（INTEGER）。 */
    protected static final String CLIENT_PARAMETERS = "[{\"name\":\"clientId\",\"type\":\"INTEGER\",\"required\":true}]";

    @Autowired
    protected DataQueryFixture fixture;

    @Autowired
    protected DataQueryMocker dataQuery;

    @Autowired
    protected DataQueryTestMapper dataQueryRows;

    @Autowired
    protected CompensationEventScanner scanner;

    /** 被查数据源 ID。 */
    protected long dataSourceId;

    /** 被查库就绪并插入一个 ACTIVE 只读数据源。 */
    @BeforeEach
    void setUpDataQueryWorkflowMocks() {
        fixture.assertReady();
        dataSourceId = dataQuery.activeReaderSource("WF_FIXTURE");
    }

    /** 插入 ACTIVE、不分页、最多 1 行、超时 5 秒的查询模板。 */
    protected long queryTemplate(String code, String body, String parameters, String columns) {
        return queryTemplate(code, body, parameters, columns, 5, QueryTemplateStatus.ACTIVE);
    }

    /** 插入不分页、最多 1 行的查询模板。 */
    protected long queryTemplate(String code, String body, String parameters, String columns, int timeoutSeconds,
        QueryTemplateStatus status) {
        return dataQuery.template(code, dataSourceId, body, parameters, columns,
            DataQueryMocker.constraints(1, timeoutSeconds, QueryPagingMode.NONE, null, false), status);
    }

    /** CLIENT 查询模板（参数 clientId，未声明出参列）。 */
    protected long clientQuery(String code) {
        return queryTemplate(code, CLIENT_BODY, CLIENT_PARAMETERS, null);
    }

    /** DATA_QUERY 动作模板配置。 */
    protected static ObjectNode dataQueryConfig(String queryCode, List<String> parameters, JsonNode outputs) {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.put("queryCode", queryCode);
        ArrayNode keys = config.putArray("parameters");
        parameters.forEach(keys::add);
        config.set("outputs", outputs);
        config.put("descriptionKey", "workflow.dataQuery.criteria." + queryCode);
        config.put("instruction", "Client 已登记");
        return config;
    }

    /** 输出声明数组：每两个参数为一组 name、type；ENUM 的可选值由 {@link #enumOutput} 追加。 */
    protected static ArrayNode outputs(String... nameTypePairs) {
        ArrayNode outputs = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < nameTypePairs.length; index += 2) {
            outputs.addObject().put("name", nameTypePairs[index]).put("type", nameTypePairs[index + 1]);
        }
        return outputs;
    }

    /** 追加一个 ENUM 输出。 */
    protected static ArrayNode enumOutput(ArrayNode outputs, String name, String... values) {
        ArrayNode enumValues = outputs.addObject().put("name", name).put("type", "ENUM").putArray("enumValues");
        List.of(values).forEach(enumValues::add);
        return outputs;
    }

    /** 新建 DATA_QUERY 动作模板（参数 clientId，输出 client_type ENUM），返回 ID。 */
    protected long clientCheckTemplate(String code, String queryCode) {
        return template(code, WorkflowActionType.DATA_QUERY.getCode(), dataQueryConfig(queryCode, List.of("clientId"),
            enumOutput(outputs("client_no", "TEXT"), "client_type", "ORGANIZATION", "INDIVIDUAL")));
    }

    /** 只有一个 DATA_QUERY 任务节点（动作 check，clientId 取启动值）的流程图。 */
    protected JsonNode singleCheckGraph(long checkTemplateId, long executorId) {
        return WorkflowGraphJson.graph().task("n1", "系统检查").action("check", checkTemplateId, executorId, true)
            .input("clientId", "VARIABLE", "start.clientId").end("end").chain("start", "n1", "end").build();
    }

    /** 按编码发起。 */
    protected PlatformResponseChecker startByCode(String definitionCode, Operator starter, String clientId,
        String businessKey) {
        return post(RUNTIME + "/instances", starter, new StartWorkflowInstanceRequestBody(null, null,
            "上线 " + clientId, businessKey, WorkflowGraphJson.values("clientId", clientId), null, null,
            definitionCode));
    }

    /**
     * 等待首查任务全部结束；同一实例多个 DATA_QUERY 同时落库时，取不到实例锁的任务按可重试结束，
     * 这里把它们置为到期并经正式 Scanner 再执行，最多三轮。
     */
    protected void awaitChecks() {
        workflowFixture.awaitIdle();
        for (int round = 0; round < 3 && workflowData.events().stream()
            .anyMatch(event -> "ERROR".equals(event.get("execute_status"))); round++) {
            workflowData.due();
            scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(WorkflowDataQueryCheckHandler.EVENT_TYPE)));
            workflowFixture.awaitIdle();
        }
    }

    /** 某动作最新一轮运行行。 */
    protected Map<String, Object> actionRow(long instanceId, String actionKey) {
        return workflowData.actionRuns(instanceId).stream().filter(row -> actionKey.equals(row.get("action_key")))
            .reduce((first, last) -> last).orElseThrow(() -> new AssertionError("没有动作运行：" + actionKey));
    }
}
