package com.paytech.client.console.bff.integration;

import java.util.List;

import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;

/**
 * Platform 参考数据目录的出站端口：只读取 ACTIVE 条目，供入驻表单组装选项
 * <li>回执缺少列表、条目缺少编码或名称、状态不是 ACTIVE、行政区不属于请求的国家时，按上游无效响应抛出。</li>
 * <li>不重试；超时与不可用按依赖失败抛出，由调用方让整页失败，不返回缺项的选项。</li>
 */
public interface PlatformReferenceDataClient {

    /**
     * 读取全部 ACTIVE 国家与地区
     *
     * @return 按国家代码升序的国家与地区
     */
    List<ReferenceCountryResponseBody> activeCountries();

    /**
     * 读取全部 ACTIVE 币种
     *
     * @return 按币种代码升序的币种
     */
    List<ReferenceCurrencyResponseBody> activeCurrencies();

    /**
     * 读取 ACTIVE 一级行政区
     *
     * @param countryCode 可选 ISO 3166-1 alpha-2 国家代码；为空时返回全部国家的行政区
     * @return 按行政区代码升序的行政区；该国没有收录行政区时为空列表
     */
    List<CountrySubdivisionResponseBody> activeSubdivisions(String countryCode);
}
