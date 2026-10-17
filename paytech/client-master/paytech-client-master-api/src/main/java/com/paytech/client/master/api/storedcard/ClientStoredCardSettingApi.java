package com.paytech.client.master.api.storedcard;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.client.master.api.storedcard.response.ClientStoredCardSettingResponseBody;

/** Client 级已存卡开关的只读查询（D42）；首期没有写入入口。 */
@HttpExchange(accept = "application/json")
public interface ClientStoredCardSettingApi {
    /**
     * 读取 Client 是否开通存卡与已存卡支付。
     *
     * @param clientId Client ID，规范正十进制 BIGINT 字符串
     * @return 开关；没有记录视为未开通，不区分 Client 是否存在
     */
    @GetExchange("/client-master/v1/clients/{clientId}/stored-card-setting")
    ClientStoredCardSettingResponseBody storedCardSetting(@PathVariable("clientId") String clientId);
}
