package com.paytech.platform.support.api.referencedata;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.referencedata.response.ReferenceCountryListResponseBody;

/**
 * 参考数据目录：国家与地区查询契约；Platform 为 t_ref_country 的 Owner，内部服务经私网读取，不经过 Gateway。
 * <li>只读；返回编码、标准属性、名称、平台区域、状态与版本，有国旗的同时返回文件地址与公开地址。</li>
 * <li>旧接口 {@link CountryRegionQueryApi} 保持不变，继续供只需区域映射的调用方使用。</li>
 */
@HttpExchange(url = "/platform/v1/reference-data", accept = MediaType.APPLICATION_JSON_VALUE)
public interface ReferenceCountryQueryApi {

    /**
     * 查询国家与地区
     * <li>条件都可选，为空不限制；多个条件同时生效。</li>
     * <li>{@code codes} 中不存在的编码不报错，只是不出现在结果中，由调用方判断。</li>
     *
     * @param status 可选状态，{@code ReferenceDataStatus} Code：DRAFT、ACTIVE、INACTIVE
     * @param codes 可选 ISO 3166-1 alpha-2 两位大写国家代码，逗号分隔，最多 50 个
     * @return 按国家代码升序的国家与地区；没有匹配时 items 为空
     *         <li>状态不合法、编码格式不合法或超过 50 个返回 400。</li>
     */
    @GetExchange("/countries")
    ReferenceCountryListResponseBody listCountries(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "codes", required = false) String codes);
}
