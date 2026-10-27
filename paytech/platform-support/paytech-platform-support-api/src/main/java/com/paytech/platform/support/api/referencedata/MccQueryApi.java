package com.paytech.platform.support.api.referencedata;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;

/**
 * 参考数据目录：MCC（Merchant Category Code）查询契约；Platform 为 t_ref_mcc 的 Owner，内部服务经私网读取，不经过 Gateway。
 * <li>只读；条目较多（约 600 行），分页返回。</li>
 */
@HttpExchange(url = "/platform/v1/reference-data", accept = MediaType.APPLICATION_JSON_VALUE)
public interface MccQueryApi {

    /**
     * 分页搜索 MCC
     * <li>条件都可选，为空不限制；多个条件同时生效；按 MCC 编码升序。</li>
     * <li>{@code codes} 中不存在的编码不报错，只是不出现在结果中，由调用方判断。</li>
     *
     * @param keyword 可选关键字：匹配 MCC 编码前缀或英文名（不区分大小写的包含匹配）
     * @param codes 可选四位数字 MCC 编码，逗号分隔，最多 50 个
     * @param status 可选状态，{@code ReferenceDataStatus} Code：DRAFT、ACTIVE、INACTIVE
     * @param page 页码，从 1 开始；为空默认 1
     * @param size 每页条数，允许 10、20、50、100、200；为空默认 10
     * @return 分页结果，空页保留总数
     *         <li>状态、编码、页码或每页条数不合法、编码超过 50 个返回 400。</li>
     */
    @GetExchange("/mccs")
    PagingResponseBody<MccResponseBody> searchMccs(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "codes", required = false) String codes,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
