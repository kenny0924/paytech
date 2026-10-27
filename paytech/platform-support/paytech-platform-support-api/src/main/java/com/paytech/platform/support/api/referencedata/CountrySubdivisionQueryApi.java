package com.paytech.platform.support.api.referencedata;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionListResponseBody;

/**
 * 参考数据目录：一级行政区查询契约；Platform 为 t_ref_country_subdivision 的 Owner，内部服务经私网读取，不经过 Gateway。
 * <li>只读；编码采用 ISO 3166-2（例如 AE-DU），本期收录 AE、SA、EG。</li>
 */
@HttpExchange(url = "/platform/v1/reference-data", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CountrySubdivisionQueryApi {

    /**
     * 查询行政区
     * <li>条件都可选，为空不限制；多个条件同时生效。</li>
     * <li>{@code codes} 中不存在的编码不报错，只是不出现在结果中，由调用方判断。</li>
     *
     * @param countryCode 可选 ISO 3166-1 alpha-2 两位大写国家代码；没有收录行政区的国家返回空列表
     * @param status 可选状态，{@code ReferenceDataStatus} Code：DRAFT、ACTIVE、INACTIVE
     * @param codes 可选 ISO 3166-2 行政区代码，逗号分隔，最多 50 个
     * @return 按行政区代码升序的行政区；没有匹配时 items 为空
     *         <li>国家代码、状态或编码格式不合法、编码超过 50 个返回 400。</li>
     */
    @GetExchange("/country-subdivisions")
    CountrySubdivisionListResponseBody listSubdivisions(
        @RequestParam(value = "countryCode", required = false) String countryCode,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "codes", required = false) String codes);
}
