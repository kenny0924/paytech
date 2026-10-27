package com.paytech.client.master.test.mocker.platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/**
 * Platform 参考数据（/platform/v1/reference-data/*）的 DTO 配置与调用记录
 * <li>按目录配置条目；某目录未配置时查询返回 null，由 Mock 控制器转为意外调用。已配置目录中不存在的编码按 Platform 合同不出现在结果中。</li>
 * <li>全局 BeforeEach 配置 {@link #mockStandardCatalog()} 公共基线；不可用、停用与不存在的场景在用例内覆盖。</li>
 */
@TestComponent
public class ReferenceDataMocker implements Clear {
    /** 参考数据目录。 */
    public enum Catalog {
        /** 国家与地区。 */
        COUNTRY,

        /** 币种。 */
        CURRENCY,

        /** 一级行政区。 */
        SUBDIVISION,

        /** 行业。 */
        INDUSTRY,

        /** MCC。 */
        MCC
    }

    /**
     * 一条配置的参考数据
     *
     * @param code 编码
     * @param status 状态 Code
     * @param countryCode 行政区所属国家；其他目录为 null
     * @param level 行业层级；其他目录为 null
     */
    public record Entry(String code, String status, String countryCode, Integer level) {
    }

    /** 一次查询故障。 */
    public record Fault(int status, ApiProblemResponse problem) {
    }

    /** 一次查询记录：目录与请求的 codes 参数原文。 */
    public record Call(Catalog catalog, String codes) {
    }

    private final Map<Catalog, Map<String, Entry>> catalogs = new EnumMap<>(Catalog.class);
    private final Map<Catalog, Fault> faults = new EnumMap<>(Catalog.class);
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    /**
     * 公共基线：本服务测试会填写的 ACTIVE 编码
     * <li>国家 AE、SA、EG、GB、US；币种 USD、AED、SAR、EGP；行政区 AE-DU、AE-AZ、AE-SH、EG-C、EG-ALX、SA-01；
     * 行业大类 RETAIL、SOFTWARE_SAAS 及细类 RETAIL_GENERAL、RETAIL_GROCERY、SAAS_SUBSCRIPTION；MCC 5399、5411、5734、7372。</li>
     */
    public synchronized void mockStandardCatalog() {
        Arrays.asList("AE", "SA", "EG", "GB", "US").forEach(code -> mock(Catalog.COUNTRY, code, ReferenceDataStatus.ACTIVE));
        Arrays.asList("USD", "AED", "SAR", "EGP").forEach(code -> mock(Catalog.CURRENCY, code, ReferenceDataStatus.ACTIVE));
        mockSubdivision("AE-DU", "AE", ReferenceDataStatus.ACTIVE);
        mockSubdivision("AE-AZ", "AE", ReferenceDataStatus.ACTIVE);
        mockSubdivision("AE-SH", "AE", ReferenceDataStatus.ACTIVE);
        mockSubdivision("EG-C", "EG", ReferenceDataStatus.ACTIVE);
        mockSubdivision("EG-ALX", "EG", ReferenceDataStatus.ACTIVE);
        mockSubdivision("SA-01", "SA", ReferenceDataStatus.ACTIVE);
        mockIndustry("RETAIL", 1, ReferenceDataStatus.ACTIVE);
        mockIndustry("SOFTWARE_SAAS", 1, ReferenceDataStatus.ACTIVE);
        mockIndustry("RETAIL_GENERAL", 2, ReferenceDataStatus.ACTIVE);
        mockIndustry("RETAIL_GROCERY", 2, ReferenceDataStatus.ACTIVE);
        mockIndustry("SAAS_SUBSCRIPTION", 2, ReferenceDataStatus.ACTIVE);
        Arrays.asList("5399", "5411", "5734", "7372").forEach(code -> mock(Catalog.MCC, code, ReferenceDataStatus.ACTIVE));
    }

    /** 配置或覆盖国家、币种或 MCC。 */
    public synchronized void mock(Catalog catalog, String code, ReferenceDataStatus status) {
        put(catalog, new Entry(code, status.getCode(), null, null));
    }

    /** 配置或覆盖行政区。 */
    public synchronized void mockSubdivision(String code, String countryCode, ReferenceDataStatus status) {
        put(Catalog.SUBDIVISION, new Entry(code, status.getCode(), countryCode, null));
    }

    /** 配置或覆盖行业。 */
    public synchronized void mockIndustry(String code, int level, ReferenceDataStatus status) {
        put(Catalog.INDUSTRY, new Entry(code, status.getCode(), null, level));
    }

    /** 从已配置目录中移除编码，使 Platform 查询不返回它。 */
    public synchronized void remove(Catalog catalog, String code) {
        catalogs.getOrDefault(catalog, new LinkedHashMap<>()).remove(code);
    }

    /** 某目录查询返回 503。 */
    public synchronized void mockUnavailable(Catalog catalog) {
        faults.put(catalog, new Fault(503, new ApiProblemResponse(ApiProblemProfile.TYPE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
            "测试依赖不可用", "SERVICE_UNAVAILABLE", "cct-onboarding-test", "2026-10-27T00:00:00.000000Z", null)));
    }

    /**
     * 一次查询：记录调用并按 codes 返回已配置条目
     *
     * @param catalog 目录
     * @param codes 请求的 codes 参数原文（逗号分隔）
     * @return 条目列表（按请求顺序，只含已配置编码）、Fault，或目录未配置时 null
     */
    public synchronized Object lookup(Catalog catalog, String codes) {
        calls.add(new Call(catalog, codes));
        Fault fault = faults.get(catalog);
        if (fault != null) {
            return fault;
        }
        Map<String, Entry> entries = catalogs.get(catalog);
        if (entries == null || codes == null) {
            return null;
        }
        return Arrays.stream(codes.split(",")).map(entries::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 某目录的查询记录快照。 */
    public List<Call> calls(Catalog catalog) {
        return new ArrayList<>(calls.stream().filter(call -> call.catalog() == catalog).toList());
    }

    /** 全部查询次数。 */
    public long count() {
        return calls.size();
    }

    /** 加入目录。 */
    private void put(Catalog catalog, Entry entry) {
        catalogs.computeIfAbsent(catalog, ignored -> new LinkedHashMap<>()).put(entry.code(), entry);
    }

    /** 每例清空配置与记录。 */
    @Override
    public synchronized void clear() {
        catalogs.clear();
        faults.clear();
        calls.clear();
    }
}
