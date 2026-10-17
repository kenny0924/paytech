package com.paytech.platform.support.api.referencedata;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

/**
 * 全平台币种规则查询契约（D41）：Platform 为 t_currency_spec 的 Owner；内部服务经私网读取，不经过 Gateway。
 * <li>只读；返回全部状态的规则，调用方按 status 判断是否允许新业务使用。</li>
 */
@HttpExchange(url = "/platform/v1/currency-specs", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CurrencySpecQueryApi {

    /**
     * 查询全部币种规则
     *
     * @return 按币种代码升序的全部规则；没有规则时 items 为空
     */
    @GetExchange
    CurrencySpecListResponseBody listCurrencySpecs();

    /**
     * 按 ISO 4217 币种代码查询规则
     *
     * @param currency 三位大写字母币种代码
     * @return 该币种的规则
     *         <li>400 表示币种代码格式不合法；404 表示该币种没有规则。</li>
     */
    @GetExchange("/{currency}")
    CurrencySpecResponseBody getCurrencySpec(@PathVariable("currency") String currency);
}
