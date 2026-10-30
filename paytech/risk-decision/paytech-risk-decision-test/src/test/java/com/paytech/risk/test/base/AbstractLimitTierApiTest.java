package com.paytech.risk.test.base;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.api.request.CreateVariableRequestBody;
import com.paytech.risk.decision.api.request.ReplaceMembersRequestBody;
import com.paytech.risk.test.checker.control.LimitTierDataChecker;
import com.paytech.risk.test.checker.control.LimitTierResponseChecker;
import com.paytech.risk.test.mapper.control.LimitTierTestMapper;
import com.paytech.risk.test.mocker.control.LimitTierMocker;

/**
 * 限额档位接口测试的公共入口辅助：以指定 Operations 账号发出真实 HTTP 请求，返回档位 Checker
 * <li>继承承保入口辅助，用于经真实承保接口准备有效承保（CRR 与观察期目标的来源）。</li>
 * <li>不含 @Test；档位、变更记录与名单成员由公共数据清理器在全局 AfterEach 回收。</li>
 */
public abstract class AbstractLimitTierApiTest extends AbstractUnderwritingApiTest {
    /** 档位申请人账号。 */
    protected static final long REQUESTER = 70201L;

    /** 档位审批人账号。 */
    protected static final long APPROVER = 70202L;

    /** 档位接口根路径。 */
    protected static final String TIERS = "/risk-limit-tiers/v1";

    @Autowired
    protected LimitTierMocker tierMocker;

    @Autowired
    protected LimitTierTestMapper tierStore;

    @Autowired
    protected LimitTierDataChecker tierChecker;

    /** 以指定账号发 GET。 */
    protected LimitTierResponseChecker tierGet(long actor, String path) {
        return tierSend(HttpMethod.GET, headers(actor), path, null);
    }

    /** 以指定账号发 POST。 */
    protected LimitTierResponseChecker tierPost(long actor, String path, Object body) {
        return tierSend(HttpMethod.POST, headers(actor), path, body);
    }

    /** 以指定私网调用方发迁移请求；调用方为空时不带 X-Caller-Service。 */
    protected LimitTierResponseChecker migrate(String caller, Object body) {
        HttpHeaders headers = TestRequestHeaders.internal("risk-limit-tier-" + UUID.randomUUID());
        if (caller != null) {
            headers.set("X-Caller-Service", caller);
        }
        return tierSend(HttpMethod.POST, headers, TIERS + "/internal/tier-migrations", body);
    }

    /** 经变量配置接口创建风控变量并要求成功。 */
    protected void createVariable(CreateVariableRequestBody body) {
        tierSend(HttpMethod.POST, headers(APPROVER), "/decision-variables/v1/variables", body).assertStatus(201);
    }

    /** 经变量配置接口替换集合成员并要求成功。 */
    protected void replaceMembers(String variableCode, ReplaceMembersRequestBody body) {
        tierSend(HttpMethod.PUT, headers(APPROVER), "/decision-variables/v1/variables/" + variableCode + "/members", body)
            .assertStatus(200);
    }

    /** 发出真实 HTTP 请求。 */
    private LimitTierResponseChecker tierSend(HttpMethod method, HttpHeaders headers, String path, Object body) {
        return new LimitTierResponseChecker(http.exchange(method, path, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
