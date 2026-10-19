package com.paytech.billing.test.base;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.request.ApproveBillingFactRequestBody;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;
import com.paytech.billing.test.dal.BillingTestMapper;

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

    @Autowired
    protected BillingTestMapper testMapper;

    @Value("${billing.event.instance-id}")
    protected String instanceId;

    /** 提交事实。 */
    protected BillingResponseChecker submit(BillingFactRequestBuilder builder) {
        return post(PATH, builder.build());
    }

    /** 提交并断言 201，返回事实 ID。 */
    protected long submitDraft(BillingFactRequestBuilder builder) {
        return submit(builder).assertSuccess(201).assertText("status", BillingFactStatus.DRAFT.getCode()).id("factId");
    }

    /** 批准事实。 */
    protected BillingResponseChecker approve(long factId, String checkerId) {
        ApproveBillingFactRequestBody body = new ApproveBillingFactRequestBody();
        body.setCheckerId(checkerId);
        return post(PATH + "/" + factId + "/approve", body);
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
