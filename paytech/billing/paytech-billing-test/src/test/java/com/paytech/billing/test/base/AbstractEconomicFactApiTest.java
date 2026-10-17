package com.paytech.billing.test.base;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.request.ApproveEconomicFactRequestBody;
import com.paytech.billing.test.builder.EconomicFactRequestBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;
import com.paytech.billing.test.dal.BillingTestMapper;

/**
 * 经济事实接口测试的公共调用
 * <li>经济事实表不预置数据：事实由用例经真实接口提交与批准，或由 {@link BillingTestMapper#insertEconomicFact} 插入后续业务切片才会生成的
 * 前置事实；全局清理组件全部清空。</li>
 */
public abstract class AbstractEconomicFactApiTest extends MockTestBase {
    /** 经济事实接口根路径。 */
    protected static final String PATH = "/billing/v1/economic-facts";

    /** 与编制人不同的复核人。 */
    protected static final String CHECKER_ID = "81002";

    /** 第二位复核人。 */
    protected static final String OTHER_CHECKER_ID = "81003";

    @Autowired
    protected BillingTestMapper testMapper;

    @Value("${billing.event.instance-id}")
    protected String instanceId;

    /** 提交事实。 */
    protected BillingResponseChecker submit(EconomicFactRequestBuilder builder) {
        return post(PATH, builder.build());
    }

    /** 提交并断言 201，返回事实 ID。 */
    protected long submitDraft(EconomicFactRequestBuilder builder) {
        return submit(builder).assertSuccess(201).assertText("status", EconomicFactStatus.DRAFT.getCode()).id("factId");
    }

    /** 批准事实。 */
    protected BillingResponseChecker approve(long factId, String checkerId) {
        ApproveEconomicFactRequestBody body = new ApproveEconomicFactRequestBody();
        body.setCheckerId(checkerId);
        return post(PATH + "/" + factId + "/approve", body);
    }

    /** 提交、批准并等待发布完成，返回事实 ID。 */
    protected long submitApprovedAndPublished(EconomicFactRequestBuilder builder) {
        long factId = submitDraft(builder);
        approve(factId, CHECKER_ID).assertSuccess(200);
        events.awaitEnvelope(factId);
        return factId;
    }

    /** 查询事实详情。 */
    protected BillingResponseChecker getFact(long factId) {
        return get(PATH + "/" + factId);
    }

    /** 事实头库内状态、版本与发布字段。 */
    protected Map<String, Object> factRow(long factId) {
        return testMapper.selectEconomicFact(factId);
    }
}
