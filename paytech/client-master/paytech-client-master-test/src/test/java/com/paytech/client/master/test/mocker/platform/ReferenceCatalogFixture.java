package com.paytech.client.master.test.mocker.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataMocker;

/**
 * client-master 用例的 Platform 参考数据目录：按编码维护期望条目，每次变更后整类写入参考数据测试支持模块的 {@link ReferenceDataMocker}，
 * 并经真实 {@link ReferenceDataRefresher} 刷新进程内快照（Mock 配置关闭了自动刷新）
 * <li>同一编码再次配置时覆盖原条目，避免向 Mocker 追加重复编码（重复编码会让该种类整体加载失败）。</li>
 * <li>全局 BeforeEach 调用 {@link #mockStandardCatalog()} 装入公共基线；停用、不存在与 Platform 故障在用例内覆盖。</li>
 */
@TestComponent
public class ReferenceCatalogFixture implements Clear {
    private final ReferenceDataMocker mocker;
    private final ReferenceDataRefresher refresher;
    private final Map<String, ReferenceDataBuilders.CountryBuilder> countries = new LinkedHashMap<>();
    private final Map<String, ReferenceDataBuilders.CurrencyBuilder> currencies = new LinkedHashMap<>();
    private final Map<String, ReferenceDataBuilders.SubdivisionBuilder> subdivisions = new LinkedHashMap<>();
    private final Map<String, ReferenceDataBuilders.IndustryBuilder> industries = new LinkedHashMap<>();
    private final Map<String, ReferenceDataBuilders.MccBuilder> mccs = new LinkedHashMap<>();

    /**
     * 写入外部 DTO Mocker 并刷新快照
     *
     * @param mocker 参考数据测试支持模块的 Mocker
     * @param refresher 生产刷新入口
     */
    public ReferenceCatalogFixture(ReferenceDataMocker mocker, ReferenceDataRefresher refresher) {
        this.mocker = mocker;
        this.refresher = refresher;
    }

    /**
     * 公共基线：本服务测试会填写的 ACTIVE 编码
     * <li>国家 AE、SA、EG、GB、US；币种 USD、AED、SAR、EGP；行政区 AE-DU、AE-AZ、AE-SH、EG-C、EG-ALX、SA-01；
     * 行业大类 RETAIL、SOFTWARE_SAAS 及细类 RETAIL_GENERAL、RETAIL_GROCERY、SAAS_SUBSCRIPTION；MCC 5399、5411、5734、7372。</li>
     */
    public synchronized void mockStandardCatalog() {
        List.of("AE", "SA", "EG", "GB", "US").forEach(code -> putCountry(code, ReferenceDataStatus.ACTIVE));
        List.of("USD", "AED", "SAR", "EGP").forEach(code -> putCurrency(code, ReferenceDataStatus.ACTIVE));
        List.of("AE-DU", "AE-AZ", "AE-SH", "EG-C", "EG-ALX", "SA-01")
            .forEach(code -> putSubdivision(code, code.substring(0, 2), ReferenceDataStatus.ACTIVE));
        putIndustry("RETAIL", null, ReferenceDataStatus.ACTIVE);
        putIndustry("SOFTWARE_SAAS", null, ReferenceDataStatus.ACTIVE);
        putIndustry("RETAIL_GENERAL", "RETAIL", ReferenceDataStatus.ACTIVE);
        putIndustry("RETAIL_GROCERY", "RETAIL", ReferenceDataStatus.ACTIVE);
        putIndustry("SAAS_SUBSCRIPTION", "SOFTWARE_SAAS", ReferenceDataStatus.ACTIVE);
        List.of("5399", "5411", "5734", "7372").forEach(code -> putMcc(code, ReferenceDataStatus.ACTIVE));
        refresh();
    }

    /** 配置或覆盖国家、币种或 MCC，并刷新快照。 */
    public synchronized void mock(ReferenceDataKind kind, String code, ReferenceDataStatus status) {
        switch (kind) {
            case COUNTRY -> putCountry(code, status);
            case CURRENCY -> putCurrency(code, status);
            case MCC -> putMcc(code, status);
            default -> throw new IllegalArgumentException("行政区与行业使用专用方法: " + kind);
        }
        refresh();
    }

    /** 配置或覆盖行政区，并刷新快照。 */
    public synchronized void mockSubdivision(String code, String countryCode, ReferenceDataStatus status) {
        putSubdivision(code, countryCode, status);
        refresh();
    }

    /** 配置或覆盖行业（parentCode 为 null 时是大类，否则是该大类下的细类），并刷新快照。 */
    public synchronized void mockIndustry(String code, String parentCode, ReferenceDataStatus status) {
        putIndustry(code, parentCode, status);
        refresh();
    }

    /** 从目录移除编码（Platform 不再返回它），并刷新快照。 */
    public synchronized void remove(ReferenceDataKind kind, String code) {
        catalog(kind).remove(code);
        refresh();
    }

    /** 按当前期望条目重写 Mocker 的全部种类后同步刷新，必须全部成功。 */
    public synchronized void refresh() {
        for (ReferenceDataKind kind : ReferenceDataKind.values()) {
            mocker.reset(kind);
        }
        mocker.country(countries.values().toArray(ReferenceDataBuilders.CountryBuilder[]::new))
            .currency(currencies.values().toArray(ReferenceDataBuilders.CurrencyBuilder[]::new))
            .subdivision(subdivisions.values().toArray(ReferenceDataBuilders.SubdivisionBuilder[]::new))
            .industry(industries.values().toArray(ReferenceDataBuilders.IndustryBuilder[]::new))
            .mcc(mccs.values().toArray(ReferenceDataBuilders.MccBuilder[]::new));
        List<ReferenceDataRefresher.Result> results = refresher.refreshNow();
        if (!results.stream().allMatch(ReferenceDataRefresher.Result::succeeded)) {
            throw new AssertionError("参考数据快照刷新失败: " + results);
        }
    }

    /** Platform 参考数据请求总次数（快照刷新才会产生请求，读快照不产生）。 */
    public long count() {
        return mocker.totalCalls();
    }

    /** 最近一次 Platform 参考数据请求的 X-Caller-Service。 */
    public String lastCallerService() {
        return mocker.lastCallerService();
    }

    /** 该种类后续 Platform 请求返回 503（已加载的快照保留）。 */
    public void mockUnavailable(ReferenceDataKind kind) {
        mocker.unavailable(kind);
    }

    /** 当前配置下同步刷新一次，返回结果（不要求成功）。 */
    public List<ReferenceDataRefresher.Result> refreshNow() {
        return refresher.refreshNow();
    }

    /** 每例清空期望条目；Mocker 由 TestBase 统一清理。 */
    @Override
    public synchronized void clear() {
        countries.clear();
        currencies.clear();
        subdivisions.clear();
        industries.clear();
        mccs.clear();
    }

    private Map<String, ?> catalog(ReferenceDataKind kind) {
        return switch (kind) {
            case COUNTRY -> countries;
            case CURRENCY -> currencies;
            case SUBDIVISION -> subdivisions;
            case INDUSTRY -> industries;
            case MCC -> mccs;
        };
    }

    private void putCountry(String code, ReferenceDataStatus status) {
        countries.put(code, ReferenceDataBuilders.country(code).status(status));
    }

    private void putCurrency(String code, ReferenceDataStatus status) {
        currencies.put(code, ReferenceDataBuilders.currency(code).status(status));
    }

    private void putSubdivision(String code, String countryCode, ReferenceDataStatus status) {
        subdivisions.put(code, ReferenceDataBuilders.subdivision(code).countryCode(countryCode).status(status));
    }

    private void putIndustry(String code, String parentCode, ReferenceDataStatus status) {
        industries.put(code, (parentCode == null ? ReferenceDataBuilders.industry(code)
            : ReferenceDataBuilders.industry(code, parentCode)).status(status));
    }

    private void putMcc(String code, ReferenceDataStatus status) {
        mccs.put(code, ReferenceDataBuilders.mcc(code).status(status));
    }
}
