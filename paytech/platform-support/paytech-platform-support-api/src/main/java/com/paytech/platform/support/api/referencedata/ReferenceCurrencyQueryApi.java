package com.paytech.platform.support.api.referencedata;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;

/**
 * 参考数据目录：币种查询契约；Platform 为 t_ref_currency 的 Owner，内部服务经私网读取，不经过 Gateway。
 * <li>只读；返回编码、标准属性、名称、状态与版本，有 logo 的同时返回文件地址与公开地址。</li>
 * <li>后端服务经 {@code paytech-platform-support-reference-data-client} 全量加载到进程内快照；旧接口 /platform/v1/currency-specs 已下线。</li>
 */
@HttpExchange(url = "/platform/v1/reference-data", accept = MediaType.APPLICATION_JSON_VALUE)
public interface ReferenceCurrencyQueryApi {

    /**
     * 查询币种
     * <li>条件都可选，为空不限制；多个条件同时生效。</li>
     * <li>{@code codes} 中不存在的编码不报错，只是不出现在结果中，由调用方判断。</li>
     *
     * @param status 可选状态，{@code ReferenceDataStatus} Code：DRAFT、ACTIVE、INACTIVE
     * @param codes 可选 ISO 4217 三位大写币种代码，逗号分隔，最多 50 个
     * @return 按币种代码升序的币种；没有匹配时 items 为空
     *         <li>状态不合法、编码格式不合法或超过 50 个返回 400。</li>
     */
    @GetExchange("/currencies")
    ReferenceCurrencyListResponseBody listCurrencies(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "codes", required = false) String codes);
}
