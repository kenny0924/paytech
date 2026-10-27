package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformReferenceDataClient;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.CountryListResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.CountrySubdivisionListResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.IndustryListResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.MccResponseBody;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * "参考数据"页的只读入口（国家、行政区、行业、MCC），全部简单转发 Platform
 * <li>每个路由由 Gateway 绑定 REFERENCE_DATA_READ（块 139），并经 PLT-I02 实时授权；查询不携带操作人。</li>
 * <li>Query 按审核页的严格边界解析：未知、重复或空 Query 与查询带 Body 均在访问 Platform 前拒绝。</li>
 * <li>维护走评审过的 SQL；币种在币种页维护，不在此处。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/reference-data-admin/v1")
public class ReferenceDataAdminController {
    /** ReferenceDataStatus 的 Code。 */
    static final String STATUS = "DRAFT|ACTIVE|INACTIVE";

    private final PlatformReferenceDataClient client;

    /** 全部国家，按国家代码升序；页面在本地搜索与筛选区域。 */
    @GetMapping("/countries")
    public CountryListResponseBody countries(@RequestParam(required = false) @Pattern(regexp = STATUS) String status) {
        return client.countries(status);
    }

    /** 行政区，可按国家筛选。 */
    @GetMapping("/country-subdivisions")
    public CountrySubdivisionListResponseBody subdivisions(
        @RequestParam(required = false) @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @RequestParam(required = false) @Pattern(regexp = STATUS) String status) {
        return client.subdivisions(countryCode, status);
    }

    /** 全部行业：每个大类之后紧跟其细类。 */
    @GetMapping("/industries")
    public IndustryListResponseBody industries(@RequestParam(required = false) @Pattern(regexp = STATUS) String status) {
        return client.industries(status);
    }

    /** 分页搜索 MCC：关键字匹配编码前缀或英文名，按编码升序。 */
    @GetMapping("/mccs")
    public PagingResponseBody<MccResponseBody> mccs(@RequestParam(required = false) @Size(max = 64) String keyword,
        @RequestParam(required = false) @Pattern(regexp = STATUS) String status,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.searchMccs(keyword == null ? null : keyword.trim(), status, paging.page(), paging.size());
    }
}
