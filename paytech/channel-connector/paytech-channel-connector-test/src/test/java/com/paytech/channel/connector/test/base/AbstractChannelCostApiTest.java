package com.paytech.channel.connector.test.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.channel.connector.api.cost.request.CancelChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostPlanRequestBody;
import com.paytech.channel.connector.api.cost.request.ReviewChannelCostVersionRequestBody;
import com.paytech.channel.connector.api.cost.request.SubmitChannelCostVersionRequestBody;
import com.paytech.channel.connector.test.builder.ChannelCostVersionRequestBuilder;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.ObjectMapper;

/**
 * 渠道成本接口测试的公共调用与断言
 * <li>四张渠道成本表不预置数据：方案、版本均由用例经真实接口创建；范围 MID 来自各测试类 BeforeEach 插入的本库 Channel / MID Fixture。</li>
 * <li>本类不放 {@code @Test}，也不声明 BeforeEach；全局空基线与清理由 ChannelConnectorMockTestBase 与 Foundation TestBase 负责。</li>
 */
public abstract class AbstractChannelCostApiTest extends ChannelConnectorMockTestBase {
    /** 渠道成本方案接口根路径。 */
    protected static final String PATH = "/channel-connector/v1/channel-cost-plans";

    /** 渠道成本命中接口路径。 */
    protected static final String RESOLUTION_PATH = "/channel-connector/v1/channel-costs/resolutions";

    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "ChannelCost.Test_01";

    /** 标准方案代码。 */
    protected static final String PLAN_CODE = "NI_STANDARD";

    /** 标准 Fixture 的渠道代码。 */
    protected static final String CHANNEL_CODE = "NI";

    /** 编制人。 */
    protected static final String MAKER_ID = "81001";

    /** 与编制人不同的复核人。 */
    protected static final String CHECKER_ID = "81002";

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ChannelTestMapper testMapper;

    /** 本例标准 Channel、技术账号与 OE 为 {@link ChannelCostVersionRequestBuilder#ENTITY_NO} 的 MID。 */
    protected Fixture fixture;

    /** 插入标准 Channel / 账号 / MID（OE AE001、币种 USD），供测试类 BeforeEach 调用。 */
    protected Fixture mockStandardChannel() {
        fixture = data.cardFixture(CHANNEL_CODE, ChannelCostVersionRequestBuilder.ENTITY_NO,
            ChannelCostVersionRequestBuilder.CURRENCY);
        return fixture;
    }

    /** 标准 Channel 主键字符串。 */
    protected String channelId() {
        return fixture.getChannel().getChannelId().toString();
    }

    /** 标准 MID 主键。 */
    protected long midId() {
        return fixture.getMid().getMidId();
    }

    /** 指定分钟前（微秒精度）。 */
    protected static Instant minutesAgo(long minutes) {
        return Instant.now().truncatedTo(ChronoUnit.MICROS).minus(minutes, ChronoUnit.MINUTES);
    }

    /** 一天后（微秒精度），批准后只到 APPROVED。 */
    protected static Instant tomorrow() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS).plus(1, ChronoUnit.DAYS);
    }

    /** 标准单范围、一分钟前生效的请求模板。 */
    protected ChannelCostVersionRequestBuilder version() {
        return version(minutesAgo(1));
    }

    /** 标准单范围、给定生效时间的请求模板。 */
    protected ChannelCostVersionRequestBuilder version(Instant effectiveFrom) {
        return ChannelCostVersionRequestBuilder.template(MAKER_ID, effectiveFrom, midId());
    }

    /** 创建方案。 */
    protected ChannelCostResponseChecker createPlan(String channelId, String planCode) {
        CreateChannelCostPlanRequestBody body = new CreateChannelCostPlanRequestBody();
        body.setChannelId(channelId);
        body.setPlanCode(planCode);
        return post(PATH, body);
    }

    /** 以标准 Channel 创建标准方案并断言成功。 */
    protected ChannelCostResponseChecker createStandardPlan() {
        return createPlan(channelId(), PLAN_CODE).assertSuccess(201);
    }

    /** 在指定方案下创建版本。 */
    protected ChannelCostResponseChecker createVersion(String planCode, ChannelCostVersionRequestBuilder builder) {
        return post(PATH + "/" + planCode + "/versions", builder.build());
    }

    /** 在标准方案下创建版本。 */
    protected ChannelCostResponseChecker createVersion(ChannelCostVersionRequestBuilder builder) {
        return createVersion(PLAN_CODE, builder);
    }

    /** 提交版本。 */
    protected ChannelCostResponseChecker submit(long costVer, String makerId) {
        SubmitChannelCostVersionRequestBody body = new SubmitChannelCostVersionRequestBody();
        body.setMakerId(makerId);
        return post(versionPath(costVer) + "/submit", body);
    }

    /** 批准版本。 */
    protected ChannelCostResponseChecker approve(long costVer, String checkerId, String approvedContentHash) {
        return post(versionPath(costVer) + "/approve", review(checkerId, approvedContentHash));
    }

    /** 驳回版本。 */
    protected ChannelCostResponseChecker reject(long costVer, String checkerId, String approvedContentHash) {
        return post(versionPath(costVer) + "/reject", review(checkerId, approvedContentHash));
    }

    /** 作废标准方案下的版本。 */
    protected ChannelCostResponseChecker cancel(long costVer, String operatorId, String reason) {
        return cancel(PLAN_CODE, costVer, operatorId, reason);
    }

    /** 作废指定方案下的版本。 */
    protected ChannelCostResponseChecker cancel(String planCode, long costVer, String operatorId, String reason) {
        CancelChannelCostVersionRequestBody body = new CancelChannelCostVersionRequestBody();
        body.setOperatorId(operatorId);
        body.setReason(reason);
        return post(PATH + "/" + planCode + "/versions/" + costVer + "/cancel", body);
    }

    /** 读取方案及版本摘要。 */
    protected ChannelCostResponseChecker getPlan(String planCode) {
        return get(PATH + "/" + planCode);
    }

    /** 读取标准方案的版本详情。 */
    protected ChannelCostResponseChecker getVersion(long costVer) {
        return get(versionPath(costVer));
    }

    /** 创建并以模板编制人提交，返回提交响应。 */
    protected ChannelCostResponseChecker createSubmitted(ChannelCostVersionRequestBuilder builder) {
        long costVer = Long.parseLong(createVersion(builder).assertSuccess(201).text("costVer"));
        return submit(costVer, MAKER_ID).assertSuccess(200);
    }

    /** 创建、提交并以版本摘要批准，返回批准响应。 */
    protected ChannelCostResponseChecker createApproved(ChannelCostVersionRequestBuilder builder) {
        ChannelCostResponseChecker submitted = createSubmitted(builder);
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
        assertNotNull(plan, "方案不存在: " + planCode);
        Object current = plan.get("current_cost_ver");
        assertEquals(status, plan.get("status"), "status");
        assertEquals(currentCostVer, current == null ? null : ((Number)current).intValue(), "当前版本号");
        assertEquals(version, ((Number)plan.get("version")).longValue(), "plan version");
    }

    /** 发送内部 POST 请求。 */
    protected ChannelCostResponseChecker post(String path, Object body) {
        return new ChannelCostResponseChecker(http.post(path, TestRequestHeaders.internal(REQUEST_ID), body),
            objectMapper, REQUEST_ID);
    }

    /** 发送内部 GET 请求。 */
    protected ChannelCostResponseChecker get(String path) {
        return new ChannelCostResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }
}
