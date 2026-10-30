package com.paytech.client.master.api.classification;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.client.master.api.classification.response.ClientBusinessClassificationResponseBody;

/** Client 经营分类的服务间只读查询（REQ-02）；首期调用方为 commercial 开通项前置门禁 ④（取主 MCC）。 */
@HttpExchange(url = "/client-master/v1", accept = "application/json")
public interface ClientBusinessClassificationApi {
    /**
     * 查询 Client 已确认的主经营分类（行业与 MCC）
     *
     * @param clientId Client ID，规范正十进制 BIGINT 字符串
     * @return 分类；Client 没有经营资料或分类未确认时 confirmed=false、两码为空（200）
     *         <li>Client 不存在或 ID 非法返回 404 CLIENT_NOT_FOUND。</li>
     */
    @GetExchange("/clients/{clientId}/business-classification")
    ClientBusinessClassificationResponseBody getBusinessClassification(@PathVariable("clientId") String clientId);
}
