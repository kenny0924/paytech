package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;

/** 十四个新增入口通过真实授权与 Owner HTTP，字典写入不携带幂等身份。 */
class RiskDictionaryBrowserBoundaryAutoTest extends OperationsBffHttpTestBase {
    @Autowired private PlatformOperationsSimulator platform;
    @Autowired private RiskDecisionConfigurationSimulator risk;
    @LocalServerPort private int port;
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final String ROOT="/decision-dictionaries/v1/";
    /** 准备当前用例的隔离会话及外部服务 Simulator。 */
    @BeforeEach void prepare(){platform.reset();platform.useRiskIdentity();risk.reset();}
    /** 清理本用例的登录与 Simulator 状态，避免用例相互污染。 */
    @AfterEach void clean(){try{assertTrue(platform.unexpectedRequests().isEmpty(),platform.unexpectedRequests().toString());assertTrue(risk.unexpectedRequests().isEmpty(),risk.unexpectedRequests().toString());}finally{platform.reset();risk.reset();}}
    /** 经真实 BFF HTTP 验证六条字典路由及可信身份转发，不发送字典幂等键。 */
    @ParameterizedTest @ValueSource(strings={"rejection-reasons","subject-tags"})
    void dictionaryLifecycleUsesSixExactRoutesWithoutIdempotency(String kind)throws Exception {
        String route=kind.equals("rejection-reasons")?"operations-risk-rejection-reason-":"operations-risk-subject-tag-";
        Map<String,Object> body=new LinkedHashMap<>();body.put(kind.equals("rejection-reasons")?"reasonCode":"tagCode","REVIEW_REQUIRED");body.put("name","需要复核");body.put("description",null);if(kind.equals("rejection-reasons"))body.put("defaultMessage","请人工复核");
        assertSuccess(send("POST",ROOT+kind,route+"create",JSON.writeValueAsString(body)));
        assertSuccess(send("GET",ROOT+kind+"?page=1&size=10&status=ACTIVE",route+"list",null));
        assertSuccess(send("GET",ROOT+kind+"/REVIEW_REQUIRED",route+"detail",null));
        body.remove(kind.equals("rejection-reasons")?"reasonCode":"tagCode");body.put("expectedVersion","1");body.put("name","更新名称");
        assertSuccess(send("PUT",ROOT+kind+"/REVIEW_REQUIRED",route+"update",JSON.writeValueAsString(body)));
        assertSuccess(send("POST",ROOT+kind+"/REVIEW_REQUIRED/status",route+"status","{\"expectedVersion\":\"2\",\"status\":\"DISABLED\"}"));
        assertSuccess(send("GET",ROOT+kind+"/REVIEW_REQUIRED/references?page=1&size=10&scope=ALL",route+"references",null));
        assertEquals(6,risk.requests().size());assertEquals(6,platform.completedAuthorizationCount());
        for(var call:risk.requests()){assertNull(call.header("Idempotency-Key"));assertEquals(RISK_ACCOUNT_ID,call.header("X-Gateway-Actor-Subject"));assertEquals(SESSION_ID,call.header("X-Gateway-Session-Id"));assertEquals("rco16-browser",call.header("X-Request-Id"));}
    }
    /** 验证历史上界与发布引用筛选完整到达 Risk 查询入口。 */
    @Test void historyAndReleaseQueriesUseRootAndExactFilters()throws Exception {
        assertSuccess(send("GET","/decision-rule-sets/v1/rule-sets/90001/versions?page=1&size=10&maxVersionNo=1","operations-risk-rule-set-versions",null));
        assertSuccess(send("GET","/decision-rule-sets/v1/rule-sets/90001/release-references?page=1&size=10&environmentCode=TEST&status=DRAFT&ruleSetVersionId=30001","operations-risk-rule-set-release-references",null));
        assertEquals(2,risk.requests().size());assertEquals(2,platform.completedAuthorizationCount());
    }
    /** 验证 PLT-I02 拒绝后不调用 Risk 写入接口。 */
    @Test void deniedWriteDoesNotCallOwner()throws Exception {
        platform.allow(false);assertEquals(403,send("POST",ROOT+"subject-tags","operations-risk-subject-tag-create","{\"tagCode\":\"NEW_TAG\",\"name\":\"标签\",\"description\":null}").statusCode());assertTrue(risk.requests().isEmpty());
    }
    /** 规则读取权限可查询两种目录，但不能通过任一新增写入口绕过维护权限。 */
    @Test void readOnlyPermissionRejectsAllSixDictionaryWritesBeforeOwner() throws Exception {
        platform.grantPermissions("900000000000000536");
        for (String kind : List.of("rejection-reasons", "subject-tags")) {
            String route = kind.equals("rejection-reasons") ? "operations-risk-rejection-reason-" : "operations-risk-subject-tag-";
            assertSuccess(send("GET", ROOT + kind + "?page=1&size=10", route + "list", null));
            int before = risk.requests().size();
            for (String operation : List.of("create", "update", "status")) {
                String path = ROOT + kind + (operation.equals("create") ? "" : "/REVIEW_REQUIRED")
                    + (operation.equals("status") ? "/status" : "");
                var response = send(operation.equals("update") ? "PUT" : "POST", path, route + operation, "{}");
                assertEquals(403, response.statusCode(), response.body());
                assertEquals(before, risk.requests().size());
            }
        }
    }
    /** 验证非法字段、类型和重复 JSON 在 BFF 被拒绝。 */
    @ParameterizedTest @ValueSource(strings={"{}","{\"tagCode\":\"TAG\",\"name\":\"标签\"}","{\"tagCode\":\"TAG\",\"name\":\"标签\",\"description\":null,\"subjectTypes\":[\"CLIENT\"]}","{\"tagCode\":\"TAG\",\"name\":\"标签\",\"name\":\"重复\",\"description\":null}","{\"tagCode\":\"TAG\",\"name\":123,\"description\":null}"})
    void malformedBodiesNeverReachOwner(String body)throws Exception {assertEquals(400,send("POST",ROOT+"subject-tags","operations-risk-subject-tag-create",body).statusCode());assertTrue(risk.requests().isEmpty());}
    /** 验证超限 Body 使用统一 413 Problem 且不请求 Risk。 */
    @Test void bodyLimitUsesProblem413AndNoOwnerCall()throws Exception {
        var response=send("POST",ROOT+"subject-tags","operations-risk-subject-tag-create"," ".repeat(16385));assertEquals(413,response.statusCode(),response.body());assertEquals("application/problem+json",response.headers().firstValue("Content-Type").orElseThrow());assertTrue(risk.requests().isEmpty());
    }
    /** 验证未知、重复及空 Query 在 BFF 被拒绝。 */
    @ParameterizedTest @ValueSource(strings={"?subjectType=CLIENT","?page=1&page=2","?status="})
    void malformedQueriesNeverReachOwner(String query)throws Exception {assertEquals(400,send("GET",ROOT+"subject-tags"+query,"operations-risk-subject-tag-list",null).statusCode());assertTrue(risk.requests().isEmpty());}
    /** 验证查询接口不接收请求 Body。 */
    @Test void getBodyIsRejected()throws Exception {assertEquals(400,send("GET",ROOT+"subject-tags","operations-risk-subject-tag-list","{}").statusCode());assertTrue(risk.requests().isEmpty());}
    /** 验证非法 Owner 响应变成安全错误，既不泄露原文也不重试。 */
    @Test void badOwnerResponseIsNotExposedOrRetried()throws Exception {
        risk.corruptWire(wire->wire.put("unexpectedReceipt","private"));var response=send("GET",ROOT+"subject-tags?page=1&size=10","operations-risk-subject-tag-list",null);
        assertEquals(502,response.statusCode(),response.body());assertFalse(response.body().contains("private"));assertEquals(1,risk.requests().size());
    }
    /** 校验成功响应后解析 JSON，保留真实 HTTP 断言边界。 */
    private void assertSuccess(HttpResponse<String> response){assertEquals(200,response.statusCode(),response.body());}
    /** 通过登录会话访问 BFF 公开入口，形成实际 HTTP 请求。 */
    private HttpResponse<String> send(String method,String path,String route,String body)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin","https://operations-console.local.test"),Map.entry("X-Request-Id","rco16-browser"),Map.entry("X-Gateway-Realm","OPERATIONS"),Map.entry("X-Gateway-Channel","OPERATIONS"),Map.entry("X-Gateway-Actor-Type","PLATFORM_USER"),Map.entry("X-Gateway-Actor-Subject",RISK_ACCOUNT_ID),Map.entry("X-Gateway-Session-Id",SESSION_ID),Map.entry("X-Gateway-Route-Code",route),Map.entry("X-Gateway-Context-Type","TENANT"),Map.entry("X-Gateway-Tenant-Type","INTERNAL"),Map.entry("X-Gateway-Tenant-Id","tenant_10001"),Map.entry("X-Gateway-Tenant-Member-Id","member_10001")).forEach(builder::header);
        if(body!=null)builder.header("Content-Type","application/json");builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        try(var client=HttpClient.newHttpClient()){return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
    }
}
