package com.paytech.billing.test.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.billing.api.request.CreateChannelCostPlanRequestBody;
import com.paytech.billing.api.request.ReviewChannelCostVersionRequestBody;
import com.paytech.billing.api.request.SubmitChannelCostVersionRequestBody;
import com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;
import com.paytech.billing.test.dal.BillingTestMapper;

/**
 * 渠道成本方案接口测试的公共调用
 * <li>四张渠道成本表不预置数据：方案、版本均由用例经真实接口创建，全局清理组件全部清空。</li>
 */
public abstract class AbstractChannelCostApiTest extends MockTestBase {
    /** 渠道成本方案接口根路径。 */
    protected static final String PATH = "/channel-cost-plans/v1";

    /** 标准 Payment Channel。 */
    protected static final String CHANNEL_ID = "710001";

    /** 标准方案代码。 */
    protected static final String PLAN_CODE = "NI_STANDARD";

    /** 编制人。 */
    protected static final String MAKER_ID = "81001";

    /** 与编制人不同的复核人。 */
    protected static final String CHECKER_ID = "81002";

    @Autowired
    protected BillingTestMapper testMapper;

    /** 指定分钟前（微秒精度）。 */
    protected static Instant minutesAgo(long minutes) {
        return Instant.now().truncatedTo(ChronoUnit.MICROS).minus(minutes, ChronoUnit.MINUTES);
    }

    /** 一天后（微秒精度），批准后只到 APPROVED。 */
    protected static Instant tomorrow() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS).plus(1, ChronoUnit.DAYS);
    }

    /** 标准单范围、一分钟前生效的请求模板。 */
    protected static ChannelCostVersionRequestBuilder version() {
        return ChannelCostVersionRequestBuilder.template(MAKER_ID, minutesAgo(1));
    }

    /** 创建方案。 */
    protected BillingResponseChecker createPlan(String channelId, String planCode) {
        CreateChannelCostPlanRequestBody body = new CreateChannelCostPlanRequestBody();
        body.setChannelId(channelId);
        body.setPlanCode(planCode);
        return post(PATH, body);
    }

    /** 创建标准方案并断言成功。 */
    protected BillingResponseChecker createStandardPlan() {
        return createPlan(CHANNEL_ID, PLAN_CODE).assertSuccess(201);
    }

    /** 在指定方案下创建版本。 */
    protected BillingResponseChecker createVersion(String planCode, ChannelCostVersionRequestBuilder builder) {
        return post(PATH + "/" + planCode + "/versions", builder.build());
    }

    /** 在标准方案下创建版本。 */
    protected BillingResponseChecker createVersion(ChannelCostVersionRequestBuilder builder) {
        return createVersion(PLAN_CODE, builder);
    }

    /** 提交版本。 */
    protected BillingResponseChecker submit(long costVer, String makerId) {
        SubmitChannelCostVersionRequestBody body = new SubmitChannelCostVersionRequestBody();
        body.setMakerId(makerId);
        return post(versionPath(costVer) + "/submit", body);
    }

    /** 批准版本。 */
    protected BillingResponseChecker approve(long costVer, String checkerId, String approvedContentHash) {
        return post(versionPath(costVer) + "/approve", review(checkerId, approvedContentHash));
    }

    /** 驳回版本。 */
    protected BillingResponseChecker reject(long costVer, String checkerId, String approvedContentHash) {
        return post(versionPath(costVer) + "/reject", review(checkerId, approvedContentHash));
    }

    /** 读取方案及版本摘要。 */
    protected BillingResponseChecker getPlan(String planCode) {
        return get(PATH + "/" + planCode);
    }

    /** 读取标准方案的版本详情。 */
    protected BillingResponseChecker getVersion(long costVer) {
        return get(versionPath(costVer));
    }

    /** 创建并以模板编制人提交，返回提交响应。 */
    protected BillingResponseChecker createSubmitted(ChannelCostVersionRequestBuilder builder) {
        long costVer = Long.parseLong(createVersion(builder).assertSuccess(201).text("costVer"));
        return submit(costVer, MAKER_ID).assertSuccess(200);
    }

    /** 创建、提交并以版本摘要批准，返回批准响应。 */
    protected BillingResponseChecker createApproved(ChannelCostVersionRequestBuilder builder) {
        BillingResponseChecker submitted = createSubmitted(builder);
        return approve(Long.parseLong(submitted.text("costVer")), CHECKER_ID, submitted.text("contentHash"))
            .assertSuccess(200);
    }

    /** 标准方案下版本资源路径。 */
    protected String versionPath(long costVer) {
        return PATH + "/" + PLAN_CODE + "/versions/" + costVer;
    }

    /** 复核请求体。 */
    protected ReviewChannelCostVersionRequestBody review(String checkerId, String approvedContentHash) {
        ReviewChannelCostVersionRequestBody body = new ReviewChannelCostVersionRequestBody();
        body.setCheckerId(checkerId);
        body.setApprovedContentHash(approvedContentHash);
        return body;
    }

    /** 断言方案库内状态、当前版本号与乐观锁版本；currentCostVer 为 null 表示无当前版本。 */
    protected void assertPlanRow(String planCode, String status, Integer currentCostVer, long version) {
        Map<String, Object> plan = testMapper.selectChannelCostPlan(planCode);
        Object current = plan.get("current_cost_ver");
        assertEquals(status, plan.get("status"), "status");
        assertEquals(currentCostVer, current == null ? null : ((Number)current).intValue(), "当前版本号");
        assertEquals(version, ((Number)plan.get("version")).longValue(), "plan version");
    }
}
