package com.paytech.billing.test.base;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;
import com.paytech.billing.test.dal.BillingTestMapper;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 计费事实接口测试的公共调用
 * <li>计费事实表不预置数据：事实由用例经真实接口提交与批准，或由 {@link BillingTestMapper#insertBillingFact} 插入后续业务切片才会生成的
 * 前置事实；全局清理组件全部清空。</li>
 */
public abstract class AbstractBillingFactApiTest extends MockTestBase {
    /** 计费事实接口根路径。 */
    protected static final String PATH = "/billing/v1/facts";

    /** 与编制人不同的复核人。 */
    protected static final String CHECKER_ID = "81002";

    /** 第二位复核人。 */
    protected static final String OTHER_CHECKER_ID = "81003";

    /** Operations BFF 填入的操作人 Header。 */
    protected static final String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /** Operations BFF 填入的会话 Header。 */
    protected static final String SESSION_HEADER = "X-Gateway-Session-Id";

    /** 测试会话 ID。 */
    protected static final String SESSION_ID = "billing-test-session";

    @Autowired
    protected BillingTestMapper testMapper;

    @Value("${billing.event.instance-id}")
    protected String instanceId;

    /** 提交事实，编制人取自构建器。 */
    protected BillingResponseChecker submit(BillingFactRequestBuilder builder) {
        return post(PATH, operatorHeaders(builder.makerId()), builder.build());
    }

    /** 提交并断言 201，返回事实 ID。 */
    protected long submitDraft(BillingFactRequestBuilder builder) {
        return submit(builder).assertSuccess(201).assertText("status", BillingFactStatus.DRAFT.getCode()).id("factId");
    }

    /** 批准事实。 */
    protected BillingResponseChecker approve(long factId, String checkerId) {
        return post(PATH + "/" + factId + "/approve", operatorHeaders(checkerId), null);
    }

    /** 作废事实草稿。 */
    protected BillingResponseChecker voidFact(long factId, String operatorId) {
        return post(PATH + "/" + factId + "/void", operatorHeaders(operatorId), null);
    }

    /** 内部 JSON Header 加操作人与会话；操作人为空时不传操作人 Header。 */
    protected HttpHeaders operatorHeaders(String operatorId) {
        HttpHeaders headers = TestRequestHeaders.internal(REQUEST_ID);
        if (operatorId != null) {
            headers.set(ACTOR_HEADER, operatorId);
        }
        headers.set(SESSION_HEADER, SESSION_ID);
        return headers;
    }

    /** 提交、批准并等待发布完成，返回事实 ID。 */
    protected long submitApprovedAndPublished(BillingFactRequestBuilder builder) {
        long factId = submitDraft(builder);
        approve(factId, CHECKER_ID).assertSuccess(200);
        events.awaitEnvelope(factId);
        return factId;
    }

    /** 查询事实详情。 */
    protected BillingResponseChecker getFact(long factId) {
        return get(PATH + "/" + factId);
    }

    /** 事实头库内状态、版本、业务时间与发布字段。 */
    protected Map<String, Object> factRow(long factId) {
        return testMapper.selectBillingFact(factId);
    }
}
