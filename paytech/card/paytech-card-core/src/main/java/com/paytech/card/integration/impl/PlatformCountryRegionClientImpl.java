package com.paytech.card.integration.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.card.integration.PlatformCountryRegionClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.api.referencedata.CountryRegionQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountryRegionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountryRegionResponseBody;

import lombok.AllArgsConstructor;

/**
 * 只适配 platform-support-api 国家区域查询 DTO；HTTP 与错误解码交给 Foundation 公共客户端
 * <li>整份响应先全部校验再返回：任一条不合法即整体失败，避免用半份映射推导区域。</li>
 */
@Component
@AllArgsConstructor
public class PlatformCountryRegionClientImpl implements PlatformCountryRegionClient {
    /** ISO 3166-1 alpha-2。 */
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    /** 区域代码：大写字母与下划线，与号段 issuer_region 列宽一致。 */
    private static final Pattern REGION_CODE = Pattern.compile("[A-Z_]{1,16}");

    private final CountryRegionQueryApi countryRegionQueryApi;

    /** 列表必须存在，逐条校验并拒绝重复国家。 */
    @Override
    public Map<String, String> listCountryRegions() {
        CountryRegionListResponseBody body = countryRegionQueryApi.listCountryRegions();
        if (body == null || body.getItems() == null) {
            throw new ErrorException("Platform 国家区域响应无效: 缺少 items");
        }
        Map<String, String> regions = new HashMap<>();
        for (CountryRegionResponseBody item : body.getItems()) {
            if (item == null || item.getCountryCode() == null || !COUNTRY_CODE.matcher(item.getCountryCode()).matches()
                || item.getRegionCode() == null || !REGION_CODE.matcher(item.getRegionCode()).matches()) {
                throw new ErrorException("Platform 国家区域响应无效: 国家或区域代码非法");
            }
            if (regions.putIfAbsent(item.getCountryCode(), item.getRegionCode()) != null) {
                throw new ErrorException("Platform 国家区域响应无效: 国家重复 " + item.getCountryCode());
            }
        }
        return Map.copyOf(regions);
    }
}
