package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.referencedata.CountryRegionQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountryRegionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountryRegionResponseBody;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CountryRegion;
import com.paytech.platform.support.referencedata.domainservice.service.CountryRegionQueryService;

import lombok.AllArgsConstructor;

/** 全平台国家区域映射查询的协议适配；简单本地查询直接委托 Service，不开事务。 */
@RestController
@AllArgsConstructor
public class CountryRegionController implements CountryRegionQueryApi {

    private final CountryRegionQueryService service;

    /** 全部映射，保持 Service 的国家代码升序。 */
    @Override
    public CountryRegionListResponseBody listCountryRegions() {
        CountryRegionListResponseBody body = new CountryRegionListResponseBody();
        body.setItems(service.listCountryRegions().stream().map(CountryRegionController::item).toList());
        return body;
    }

    /** 映射行转换为响应元素。 */
    static CountryRegionResponseBody item(CountryRegion row) {
        CountryRegionResponseBody item = new CountryRegionResponseBody();
        item.setCountryCode(row.getCountryCode());
        item.setRegionCode(row.getRegionCode());
        return item;
    }
}
