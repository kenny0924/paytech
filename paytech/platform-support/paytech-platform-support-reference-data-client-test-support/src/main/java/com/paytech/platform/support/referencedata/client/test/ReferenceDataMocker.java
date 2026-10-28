package com.paytech.platform.support.referencedata.client.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryResponseBody;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders.CountryBuilder;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders.CurrencyBuilder;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders.IndustryBuilder;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders.MccBuilder;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders.SubdivisionBuilder;

/**
 * Platform 参考数据查询的外部 DTO Mocker：按种类配置 Platform 返回的全量数据或故障，记录每个种类的请求次数。
 * <li>没有配置的种类被请求时记为意外调用并返回 500 Problem；带筛选条件的请求同样记为意外（客户端只做全量加载）。</li>
 * <li>{@link #totalCalls()} 在读方法前后不变，用于证明读方法不发 HTTP。</li>
 * <li>消费方把本包加入 {@code @EnableMockHttpServer} 的扫描范围；全局 AfterEach 调用 {@link #assertNoUnexpectedCalls()}，
 * 由 TestBase 统一 {@link #clear()}。</li>
 */
@TestComponent
public class ReferenceDataMocker implements Clear {

    /** Mock 响应的 Request ID（Problem 体与响应头一致）。 */
    public static final String REQUEST_ID = "reference-data-test";

    private static final String PROBLEM_TIME = "2026-10-27T00:00:00.000000Z";

    private final Map<ReferenceDataKind, List<Object>> items = new EnumMap<>(ReferenceDataKind.class);

    private final Map<ReferenceDataKind, MockHttpFault> faults = new EnumMap<>(ReferenceDataKind.class);

    private final Map<ReferenceDataKind, Integer> calls = new EnumMap<>(ReferenceDataKind.class);

    private final List<String> unexpectedCalls = new ArrayList<>();

    private Long reportedMccTotal;

    private String lastAuthorization;

    private String lastCallerService;

    /** 追加币种（同一种类可多次追加，不去重，便于构造重复编码负例）。 */
    public synchronized ReferenceDataMocker currency(CurrencyBuilder... builders) {
        return add(ReferenceDataKind.CURRENCY, Arrays.stream(builders).map(CurrencyBuilder::build).toList());
    }

    /** 追加国家。 */
    public synchronized ReferenceDataMocker country(CountryBuilder... builders) {
        return add(ReferenceDataKind.COUNTRY, Arrays.stream(builders).map(CountryBuilder::build).toList());
    }

    /** 追加行政区。 */
    public synchronized ReferenceDataMocker subdivision(SubdivisionBuilder... builders) {
        return add(ReferenceDataKind.SUBDIVISION, Arrays.stream(builders).map(SubdivisionBuilder::build).toList());
    }

    /** 追加行业。 */
    public synchronized ReferenceDataMocker industry(IndustryBuilder... builders) {
        return add(ReferenceDataKind.INDUSTRY, Arrays.stream(builders).map(IndustryBuilder::build).toList());
    }

    /** 追加 MCC。 */
    public synchronized ReferenceDataMocker mcc(MccBuilder... builders) {
        return add(ReferenceDataKind.MCC, Arrays.stream(builders).map(MccBuilder::build).toList());
    }

    /** 把该种类配置为空列表（Platform 返回 items: []）。 */
    public synchronized ReferenceDataMocker empty(ReferenceDataKind kind) {
        items.put(kind, new ArrayList<>());
        return this;
    }

    /** 删除该种类已配置的数据与故障，之后可重新追加（模拟 Platform 数据变化）。 */
    public synchronized ReferenceDataMocker reset(ReferenceDataKind kind) {
        items.remove(kind);
        faults.remove(kind);
        return this;
    }

    /** 该种类后续请求返回指定故障，直到 {@link #recover} 或清理。 */
    public synchronized ReferenceDataMocker fault(ReferenceDataKind kind, MockHttpFault fault) {
        faults.put(kind, fault);
        return this;
    }

    /** 该种类后续请求返回 503 Problem。 */
    public synchronized ReferenceDataMocker unavailable(ReferenceDataKind kind) {
        return fault(kind, problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "合成故障: Platform 不可用"));
    }

    /** 撤销该种类的故障，恢复返回已配置的数据。 */
    public synchronized ReferenceDataMocker recover(ReferenceDataKind kind) {
        faults.remove(kind);
        return this;
    }

    /** MCC 每页响应报告的总数改为指定值（模拟翻页过程中总数与实际不一致）。 */
    public synchronized ReferenceDataMocker reportedMccTotal(long total) {
        reportedMccTotal = total;
        return this;
    }

    /**
     * 最小默认数据：币种 USD / AED / SAR / EGP；国家 AE / SA / EG / US / GB / CN；行政区 AE-DU / AE-AZ / SA-01 / EG-C；
     * 行业 RETAIL、SOFTWARE_SAAS 两个大类与其细类；MCC 5651 / 5732 / 5734 / 5999 / 7995（高风险）。全部 ACTIVE。
     */
    public synchronized ReferenceDataMocker defaults() {
        currency(ReferenceDataBuilders.currency("USD").numericCode("840"),
            ReferenceDataBuilders.currency("AED").numericCode("784"),
            ReferenceDataBuilders.currency("SAR").numericCode("682"),
            ReferenceDataBuilders.currency("EGP").numericCode("818"));
        country(country("AE", "ARE", "784", "GCC", "971", "AED"), country("SA", "SAU", "682", "GCC", "966", "SAR"),
            country("EG", "EGY", "818", "MEA", "20", "EGP"), country("US", "USA", "840", "NORTH_AMERICA", "1", "USD"),
            country("GB", "GBR", "826", "EUROPE", "44", "GBP"), country("CN", "CHN", "156", "APAC", "86", "CNY"));
        subdivision(ReferenceDataBuilders.subdivision("AE-DU").category("EMIRATE"),
            ReferenceDataBuilders.subdivision("AE-AZ").category("EMIRATE"),
            ReferenceDataBuilders.subdivision("SA-01").category("REGION"),
            ReferenceDataBuilders.subdivision("EG-C").category("GOVERNORATE"));
        industry(ReferenceDataBuilders.industry("RETAIL"),
            ReferenceDataBuilders.industry("RETAIL_APPAREL", "RETAIL").defaultMccCode("5651"),
            ReferenceDataBuilders.industry("RETAIL_ELECTRONICS", "RETAIL").defaultMccCode("5732"),
            ReferenceDataBuilders.industry("SOFTWARE_SAAS"),
            ReferenceDataBuilders.industry("SAAS_SUBSCRIPTION", "SOFTWARE_SAAS").defaultMccCode("5734"));
        return mcc(ReferenceDataBuilders.mcc("5651"), ReferenceDataBuilders.mcc("5732"), ReferenceDataBuilders.mcc("5734"),
            ReferenceDataBuilders.mcc("5999"), ReferenceDataBuilders.mcc("7995").networkHighRisk(true));
    }

    /** 该种类收到的请求次数（MCC 每页一次，含故障与意外调用）。 */
    public synchronized int callCount(ReferenceDataKind kind) {
        return calls.getOrDefault(kind, 0);
    }

    /** 全部种类收到的请求次数。 */
    public synchronized int totalCalls() {
        return calls.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** 最近一次请求的 Authorization 头（内部调用 Token）；没有时为 null。 */
    public synchronized String lastAuthorization() {
        return lastAuthorization;
    }

    /** 最近一次请求的 X-Caller-Service 头；没有时为 null。 */
    public synchronized String lastCallerService() {
        return lastCallerService;
    }

    /** 全局 AfterEach：不允许出现未配置或带筛选条件的请求。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpectedCalls.isEmpty(), () -> "存在意外的 Platform 参考数据请求: " + unexpectedCalls);
    }

    /** 每例清除配置、故障与调用记录。 */
    @Override
    public synchronized void clear() {
        items.clear();
        faults.clear();
        calls.clear();
        unexpectedCalls.clear();
        reportedMccTotal = null;
        lastAuthorization = null;
        lastCallerService = null;
    }

    /** 由 Mock API 调用：记录本次请求的调用身份头。 */
    synchronized void recordIdentity(String authorization, String callerService) {
        lastAuthorization = authorization;
        lastCallerService = callerService;
    }

    /**
     * 由 Mock API 调用：返回列表种类的全量数据
     *
     * @param kind 种类
     * @param filtered 请求是否带了筛选条件
     * @param type 元素类型
     * @return 配置的 DTO 列表
     */
    synchronized <T> List<T> answerList(ReferenceDataKind kind, boolean filtered, Class<T> type) {
        return configured(kind, filtered).stream().map(type::cast).toList();
    }

    /**
     * 由 Mock API 调用：按 page / size 真实分页返回 MCC
     *
     * @param filtered 请求是否带了筛选条件
     * @param page 页码
     * @param size 每页条数，只允许 10、20、50、100、200
     * @return 当前页与总数（可被 {@link #reportedMccTotal} 覆盖）
     */
    synchronized PagingResponseBody<MccResponseBody> answerMccPage(boolean filtered, Integer page, Integer size) {
        List<MccResponseBody> all = configured(ReferenceDataKind.MCC, filtered).stream()
            .map(MccResponseBody.class::cast).toList();
        PagingRequestBody paging;
        try {
            paging = PagingRequestBody.of(page, size);
        } catch (InvalidParameterException invalid) {
            throw unexpected(ReferenceDataKind.MCC, "非法分页 page=" + page + ", size=" + size);
        }
        int from = (int)Math.min(paging.offset(), all.size());
        int to = Math.min(from + paging.size(), all.size());
        long total = reportedMccTotal == null ? all.size() : reportedMccTotal;
        return new PagingResponseBody<>(all.subList(from, to), paging.page(), paging.size(), total);
    }

    /** 记录一次请求并返回已配置的数据；故障直接抛出，未配置或带筛选条件记为意外。 */
    private List<Object> configured(ReferenceDataKind kind, boolean filtered) {
        calls.merge(kind, 1, Integer::sum);
        if (filtered) {
            throw unexpected(kind, "带筛选条件的请求");
        }
        MockHttpFault fault = faults.get(kind);
        if (fault != null) {
            throw fault;
        }
        List<Object> configured = items.get(kind);
        if (configured == null) {
            throw unexpected(kind, "未配置");
        }
        return configured;
    }

    /** 追加已构建的 DTO。 */
    private ReferenceDataMocker add(ReferenceDataKind kind, List<?> built) {
        items.computeIfAbsent(kind, ignored -> new ArrayList<>()).addAll(built);
        return this;
    }

    /** 记录意外调用，返回 500 Problem。 */
    private MockHttpFault unexpected(ReferenceDataKind kind, String reason) {
        unexpectedCalls.add(kind.getCode() + " " + reason);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "意外的参考数据请求: " + kind.getCode() + " " + reason);
    }

    /** 合规 Problem 故障。 */
    private static MockHttpFault problem(HttpStatus status, String code, String detail) {
        return MockHttpFault.json(status.value(), new ApiProblemResponse(ApiProblemProfile.TYPE, status.getReasonPhrase(),
            detail, code, REQUEST_ID, PROBLEM_TIME, null));
    }

    /** 默认数据中的一个 ACTIVE 国家。 */
    private static CountryBuilder country(String code, String alpha3, String numeric, String region, String callingCode,
        String currency) {
        return ReferenceDataBuilders.country(code).alpha3Code(alpha3).numericCode(numeric).regionCode(region)
            .callingCode(callingCode).defaultCurrencyCode(currency).status(ReferenceDataStatus.ACTIVE);
    }
}
