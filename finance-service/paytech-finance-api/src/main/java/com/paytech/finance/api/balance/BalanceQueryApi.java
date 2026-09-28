package com.paytech.finance.api.balance;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.finance.api.balance.BalanceModels.ClientBalanceResponseBody;
import com.paytech.finance.api.balance.BalanceModels.PartnerBalanceResponseBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * FIN-I01/FIN-I02：单一 Owner、Operating Entity 和币种下的当前余额查询。
 *
 * <p>
 * 内部 HTTP 契约审核稿，尚未注册 Controller。实现前必须接入调用方身份及资源授权， 不得把请求中的 Owner ID 当作身份凭证。余额查询不等于出款资格评估。
 */
@HttpExchange(value = "/balances/v1", accept = "application/json")
public interface BalanceQueryApi {

    /**
     * 查询 Client 应付、保证金和追偿余额；同一响应来自一致的本地读取范围。
     *
     * @param clientId
     *            正十进制 Client 标识，不接受任意非数字外部编号
     * @param operatingEntityId
     *            正十进制 Operating Entity 标识；实现时还需验证 Long 范围及归属
     * @param currency
     *            大写三字母币种；格式合法不代表 Finance 已支持该币种
     * @return 本授权范围的分类余额，不提供跨主体或跨币种合计
     */
    @GetExchange("/clients/{clientId}")
    ClientBalanceResponseBody queryClientBalance(
        @PathVariable("clientId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String clientId,
        @RequestParam("operatingEntityId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String operatingEntityId,
        @RequestParam("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency);

    /**
     * 查询 Local Partner 应付与追偿余额，不返回 Client 保证金字段。
     *
     * @param partnerId
     *            正十进制 Local Partner 标识
     * @param operatingEntityId
     *            正十进制 Operating Entity 标识；不得汇总 Partner 的其他 OE
     * @param currency
     *            大写三字母币种；实现时验证业务支持范围
     * @return 本授权范围的分类余额，Available 不承诺本次出款可获批准
     */
    @GetExchange("/partners/{partnerId}")
    PartnerBalanceResponseBody queryPartnerBalance(
        @PathVariable("partnerId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String partnerId,
        @RequestParam("operatingEntityId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String operatingEntityId,
        @RequestParam("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency);
}
