package com.paytech.account.api;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * FIN-I01/FIN-I02：单一 Owner、Operating Entity 和币种下的当前余额查询；以及同一 Owner 在全部 Operating Entity 与币种下的余额列表。
 *
 * <li>Finance 内部开发接口：未接调用方身份及资源授权，不得把请求中的 Owner ID 当作身份凭证，不得对外发布。</li>
 * <li>余额查询不等于出款资格评估。</li>
 * <li>只读 account 本库 Owner 账户（t_owner_account），不调用 ledger；Owner 在该 OE 与币种下没有任何账户时 400 OWNER_ACCOUNT_NOT_FOUND
 * （{@code BalanceQueryErrorCode}），有账户但缺少某个桶时该金额为零。</li>
 */
@HttpExchange(value = "/balances/v1", accept = "application/json")
public interface BalanceQueryApi {

    /**
     * 查询 Client 应付、保证金和追偿余额；同一响应来自一致的本地读取范围。
     *
     * @param clientId
     *            正十进制 Client 标识，不接受任意非数字外部编号
     * @param entityNo
     *            Operating Entity 的 entityNo，如 AE001
     * @param currency
     *            大写三字母币种；格式合法不代表 Finance 已支持该币种
     * @return 本授权范围的分类余额，不提供跨主体或跨币种合计；无任何账户时 400 OWNER_ACCOUNT_NOT_FOUND
     */
    @GetExchange("/clients/{clientId}")
    ClientBalanceResponseBody queryClientBalance(
        @PathVariable("clientId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String clientId,
        @RequestParam("entityNo") @NotBlank @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency);

    /**
     * 查询 Local Partner 应付与追偿余额，不返回 Client 保证金字段。
     *
     * @param partnerId
     *            正十进制 Local Partner 标识
     * @param entityNo
     *            Operating Entity 的 entityNo；不汇总 Partner 的其他 OE
     * @param currency
     *            大写三字母币种；实现时验证业务支持范围
     * @return 本授权范围的分类余额，Available 不承诺本次出款可获批准；无任何账户时 400 OWNER_ACCOUNT_NOT_FOUND
     */
    @GetExchange("/partners/{partnerId}")
    PartnerBalanceResponseBody queryPartnerBalance(
        @PathVariable("partnerId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String partnerId,
        @RequestParam("entityNo") @NotBlank @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency);

    /**
     * 列出 Client 在全部 Operating Entity 与币种下的分类余额；一次读取该 Client 的全部 Owner 账户后按 entityNo × 币种聚合。
     *
     * @param clientId
     *            正十进制 Client 标识
     * @return 每个有账户的 entityNo × 币种一项，按 entityNo、币种升序；字段语义同单查询，不提供跨主体或跨币种合计；
     *         Client 没有任何账户时为空数组（不返回 OWNER_ACCOUNT_NOT_FOUND）
     */
    @GetExchange("/clients/{clientId}/positions")
    List<ClientBalanceResponseBody> listClientPositions(
        @PathVariable("clientId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String clientId);

    /**
     * 列出 Local Partner 在全部 Operating Entity 与币种下的分类余额，不返回 Client 保证金字段。
     *
     * @param partnerId
     *            正十进制 Local Partner 标识
     * @return 每个有账户的 entityNo × 币种一项，按 entityNo、币种升序；Partner 没有任何账户时为空数组
     */
    @GetExchange("/partners/{partnerId}/positions")
    List<PartnerBalanceResponseBody> listPartnerPositions(
        @PathVariable("partnerId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String partnerId);
}
