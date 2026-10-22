package com.paytech.platform.support.api.referencedata;

import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.referencedata.response.CountryRegionListResponseBody;

/**
 * 全平台国家区域映射查询契约：Platform 为 t_country_region 的 Owner；内部服务经私网读取，不经过 Gateway。
 * <li>只读；当前调用方为 card-service，导入 BIN 库时一次读取全量映射推导发卡区域。</li>
 * <li>映射只经初始化数据维护，本期没有维护接口。</li>
 */
@HttpExchange(url = "/platform/v1/country-regions", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CountryRegionQueryApi {

    /**
     * 查询全部国家区域映射
     *
     * @return 按国家代码升序的全部映射；没有映射时 items 为空
     */
    @GetExchange
    CountryRegionListResponseBody listCountryRegions();
}
