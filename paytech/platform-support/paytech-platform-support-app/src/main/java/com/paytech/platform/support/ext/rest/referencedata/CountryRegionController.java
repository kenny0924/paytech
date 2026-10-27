package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.referencedata.CountryRegionQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountryRegionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountryRegionResponseBody;
import com.paytech.platform.support.referencedata.service.processor.ListReferenceCountriesProcessor;
import com.paytech.platform.support.referencedata.service.processor.response.ReferenceCountryResult;

import lombok.AllArgsConstructor;

/**
 * 全平台国家区域映射查询的协议适配；不开事务
 * <li>旧接口响应冻结（S-01）：读取全部国家（不限状态），只输出国家代码与区域两列，字段与顺序不变。</li>
 */
@RestController
@AllArgsConstructor
public class CountryRegionController implements CountryRegionQueryApi {

    private final ReferenceDataQueryConverter converter;

    private final ListReferenceCountriesProcessor processor;

    /** 全部映射，保持国家代码升序。 */
    @Override
    public CountryRegionListResponseBody listCountryRegions() {
        CountryRegionListResponseBody body = new CountryRegionListResponseBody();
        body.setItems(processor.process(converter.countries(null, null)).stream().map(CountryRegionController::item)
            .toList());
        return body;
    }

    /** 国家结果转换为旧响应元素。 */
    static CountryRegionResponseBody item(ReferenceCountryResult result) {
        CountryRegionResponseBody item = new CountryRegionResponseBody();
        item.setCountryCode(result.getCountryCode());
        item.setRegionCode(result.getRegionCode());
        return item;
    }
}
