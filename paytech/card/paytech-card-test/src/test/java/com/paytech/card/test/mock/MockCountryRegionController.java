package com.paytech.card.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.card.test.mocker.PlatformMocker;
import com.paytech.platform.support.api.referencedata.CountryRegionQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountryRegionListResponseBody;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 Platform 国家区域映射查询（Platform Context Path 前缀由 {@link MockPlatformFileController} 统一配置）。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockCountryRegionController implements CountryRegionQueryApi {
    private final PlatformMocker platform;

    /** 全部国家区域映射。 */
    @Override
    public CountryRegionListResponseBody listCountryRegions() {
        return platform.countryRegions(MockPlatformFileController.caller());
    }
}
