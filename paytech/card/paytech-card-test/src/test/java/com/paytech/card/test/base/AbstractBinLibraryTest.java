package com.paytech.card.test.base;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders;

import tools.jackson.databind.json.JsonMapper;

/**
 * BIN 库测试的公共调用与固件：以 operations-console-bff 身份经真实 HTTP 入口访问 /card/v1/bin-library，等待异步导入任务结束；不放 {@code @Test}。
 * <li>每例由 Platform 国家参考数据（6 国合成数据）刷新一次进程内快照，作为国家区域映射；号段为公开测试卡号段与合成发卡行，不代表真实 BIN 数据。</li>
 */
public abstract class AbstractBinLibraryTest extends AbstractCardApiTest {
    /** 操作人 Operations Account ID。 */
    protected static final String ACTOR = "900000000000000123";

    /** 异步导入的最长等待时间。 */
    protected static final Duration IMPORT_WAIT = Duration.ofSeconds(20);

    /** 测试使用的国家区域映射（与 Platform 种子口径一致）。 */
    protected static final Map<String, String> REGIONS = Map.of("US", "NORTH_AMERICA", "GB", "EUROPE", "AE", "GCC", "SA",
        "GCC", "JP", "APAC", "CN", "APAC");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 运营身份的请求路径前缀。 */
    protected static final String BASE = "/card/v1/bin-library";

    /** BIN 库用例共用的国家快照：配置 6 国参考数据后同步刷新一次（自动刷新已关闭），之后导入只读内存。 */
    @BeforeEach
    protected final void setUpCountryRegions() {
        REGIONS.forEach((country, region) -> referenceData.country(ReferenceDataBuilders.country(country).regionCode(region)));
        refreshCountries();
    }

    /** 同步刷新国家快照，必须成功。 */
    protected void refreshCountries() {
        List<ReferenceDataRefresher.Result> results = referenceDataRefresher.refreshNow();
        if (!results.stream().allMatch(ReferenceDataRefresher.Result::succeeded)) {
            throw new AssertionError("国家参考数据刷新失败: " + results);
        }
    }

    /** 以 operations-console-bff 身份发请求，写请求带操作人头。 */
    protected Response ops(HttpMethod method, String path, Object body) {
        return callWithActor(method, BASE + path, CardApiConstants.OPERATIONS_CONSOLE_BFF, ACTOR, body);
    }

    /** 以给定调用方与操作人发请求（任一为 null 时不带该头），返回状态与响应文本。 */
    protected Response callWithActor(HttpMethod method, String path, String caller, String actor, Object body) {
        try {
            var request = client().method(method).uri(path);
            if (caller != null) {
                request.header(CardApiConstants.CALLER_HEADER, caller);
            }
            if (actor != null) {
                request.header(CardApiConstants.ACTOR_HEADER, actor);
            }
            if (body != null) {
                request.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
            }
            var entity = request.retrieve().toEntity(String.class);
            return new Response(entity.getStatusCode().value(), entity.getBody() == null ? "" : entity.getBody());
        } catch (org.springframework.web.client.RestClientResponseException failure) {
            return new Response(failure.getStatusCode().value(), failure.getResponseBodyAsString());
        }
    }

    /** 响应文本解析为 JSON 对象。 */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> json(Response response) {
        return JSON.readValue(response.body(), Map.class);
    }

    /** 分页响应的 items。 */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> items(Response response) {
        return (List<Map<String, Object>>)json(response).get("items");
    }

    /** 以运营身份用文件创建版本。 */
    protected Response createVersion(String fileId) {
        return ops(HttpMethod.POST, "/versions", Map.of("fileAddress", FileAddress.of(Long.parseLong(fileId)).value()));
    }

    /** 以运营身份激活或回切。 */
    protected Response activate(int versionNo, Integer expectedActiveVersionNo) {
        var body = new java.util.HashMap<String, Object>();
        body.put("expectedActiveVersionNo", expectedActiveVersionNo);
        return ops(HttpMethod.POST, "/versions/" + versionNo + "/activation", body);
    }

    /** 以运营身份查询单个 BIN。 */
    protected Response lookup(String bin) {
        return ops(HttpMethod.GET, "/bins/" + bin, null);
    }

    /** 等待某版本离开 VALIDATING 并返回其数据库行。 */
    protected Map<String, Object> awaitImported(int versionNo) {
        await().atMost(IMPORT_WAIT).until(() -> !BinLibraryVersionStatus.VALIDATING.getCode().equals(version(versionNo).get("status")));
        return version(versionNo);
    }

    /** 某版本的数据库行。 */
    protected Map<String, Object> version(int versionNo) {
        List<Map<String, Object>> versions = mapper.binVersions();
        return versions.stream().filter(row -> ((Number)row.get("versionNo")).intValue() == versionNo).findFirst()
            .orElseThrow(() -> new AssertionError("BIN 库版本不存在: v" + versionNo));
    }

    /**
     * 固件：生效版本 v1，含公开测试卡号段：411111（VISA，6 位）与 40128888（VISA DEBIT GB，8 位）、401288（VISA CREDIT US，6 位）、
     * 378282（AMEX CHARGE US）、555555（MASTERCARD CREDIT US）
     *
     * @return 版本 ID
     */
    protected long seedActiveVersion() {
        long versionId = binLibrary.version(1, BinLibraryVersionStatus.ACTIVE, 5);
        binLibrary.range(versionId, "411111", CardBrand.VISA, CardFundingType.CREDIT, "CLASSIC", CardProduct.CONSUMER, "US",
            "NORTH_AMERICA", "SYNTHETIC TEST ISSUER US");
        binLibrary.range(versionId, "401288", CardBrand.VISA, CardFundingType.CREDIT, "GOLD", CardProduct.CONSUMER, "US",
            "NORTH_AMERICA", "SYNTHETIC TEST ISSUER US");
        binLibrary.range(versionId, "40128888", CardBrand.VISA, CardFundingType.DEBIT, "CLASSIC", CardProduct.CONSUMER, "GB",
            "EUROPE", "SYNTHETIC TEST ISSUER GB");
        binLibrary.range(versionId, "378282", CardBrand.AMEX, CardFundingType.CHARGE, null, CardProduct.CONSUMER, "US",
            "NORTH_AMERICA", null);
        binLibrary.range(versionId, "555555", CardBrand.MASTERCARD, CardFundingType.CREDIT, "WORLD", CardProduct.CONSUMER,
            "US", "NORTH_AMERICA", null);
        return versionId;
    }
}
