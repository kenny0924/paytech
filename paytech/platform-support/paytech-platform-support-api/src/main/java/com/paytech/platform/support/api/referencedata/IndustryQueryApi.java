package com.paytech.platform.support.api.referencedata;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.referencedata.response.IndustryListResponseBody;

/**
 * 参考数据目录：两级行业分类查询契约；Platform 为 t_ref_industry 的 Owner，内部服务经私网读取，不经过 Gateway。
 * <li>只读；第 1 级为大类，第 2 级为细类，细类可带默认 MCC。</li>
 */
@HttpExchange(url = "/platform/v1/reference-data", accept = MediaType.APPLICATION_JSON_VALUE)
public interface IndustryQueryApi {

    /**
     * 查询行业
     * <li>条件都可选，为空不限制；多个条件同时生效。</li>
     * <li>{@code codes} 中不存在的编码不报错，只是不出现在结果中，由调用方判断。</li>
     *
     * @param level 可选层级：1 为大类，2 为细类
     * @param parentCode 可选上级大类编码，只返回其下细类
     * @param status 可选状态，{@code ReferenceDataStatus} Code：DRAFT、ACTIVE、INACTIVE
     * @param codes 可选行业编码，逗号分隔，最多 50 个
     * @return 行业列表；没有匹配时 items 为空
     *         <li>按大类 sortNo、细类 sortNo 排序：每个大类之后紧跟其细类。</li>
     *         <li>层级不是 1 或 2、状态或编码格式不合法、编码超过 50 个返回 400。</li>
     */
    @GetExchange("/industries")
    IndustryListResponseBody listIndustries(
        @RequestParam(value = "level", required = false) Integer level,
        @RequestParam(value = "parentCode", required = false) String parentCode,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "codes", required = false) String codes);
}
