package com.paytech.operations.console.bff.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.commercial.api.term.response.CommercialTermPlanResponseBody;
import com.paytech.commercial.api.term.response.CommercialTermVersionResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreateCommercialTermPlanRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CreateCommercialTermVersionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.ReplaceCommercialTermPricesRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermVersionCommandRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.ScheduleCommercialTermVersionRequestBody;

/**
 * commercial 条款写入的过渡出站合同：路径与 CommercialTermApi 相同，操作人改由 X-Gateway-Actor-Subject 请求头传递
 * <li>commercial 正在把 /commercial-terms/v1 写入从 Body 的 operatorAccountId 切到与合同 / 画像一致的请求头；
 * BFF 按目标合同编码，Body 不携带操作人。</li>
 * <li>写入 Body 直接使用 BFF 页面模型（字段与 commercial 去掉 operatorAccountId 后一一对应），响应仍用 commercial-api DTO 再严格转换。</li>
 *
 * @author Claude
 */
// TODO by CLAUDE: commercial-api 的 CommercialTermApi 发布请求头签名后删除本接口，改用 CommercialTermApi 并把 Body 转换为 Owner DTO。
@HttpExchange(url = "/commercial-terms/v1", accept = "application/json")
public interface CommercialTermCommandExchange {
    /** Gateway 传入、BFF 透传的操作者 Account ID 请求头。 */
    String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /**
     * 创建条款方案
     *
     * @param actorId 操作者
     * @param body 方案内容
     * @return 新建 201，同号同内容重放 200
     */
    @PostExchange("/plans")
    ResponseEntity<CommercialTermPlanResponseBody> createPlan(@RequestHeader(ACTOR_HEADER) String actorId,
        @RequestBody CreateCommercialTermPlanRequestBody body);

    /**
     * 创建 DRAFT 版本，可复制
     *
     * @param actorId 操作者
     * @param planId 方案 ID
     * @param body 版本属性
     * @return 201 与版本
     */
    @PostExchange("/plans/{planId}/versions")
    ResponseEntity<CommercialTermVersionResponseBody> createVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("planId") String planId, @RequestBody CreateCommercialTermVersionRequestBody body);

    /**
     * 整体替换 DRAFT 版本价格集合
     *
     * @param actorId 操作者
     * @param versionId 版本 ID
     * @param body 完整价格集合
     * @return 版本
     */
    @PutExchange("/versions/{versionId}/prices")
    CommercialTermVersionResponseBody replacePrices(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") String versionId, @RequestBody ReplaceCommercialTermPricesRequestBody body);

    /**
     * 提交审批（Maker）
     *
     * @param actorId 操作者
     * @param versionId 版本 ID
     * @param body 版本条件
     * @return 版本
     */
    @PostExchange("/versions/{versionId}/submit")
    CommercialTermVersionResponseBody submitVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") String versionId, @RequestBody CommercialTermVersionCommandRequestBody body);

    /**
     * 批准（Checker ≠ Maker）
     *
     * @param actorId 操作者
     * @param versionId 版本 ID
     * @param body 版本条件
     * @return 版本
     */
    @PostExchange("/versions/{versionId}/approve")
    CommercialTermVersionResponseBody approveVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") String versionId, @RequestBody CommercialTermVersionCommandRequestBody body);

    /**
     * 驳回（Checker ≠ Maker）
     *
     * @param actorId 操作者
     * @param versionId 版本 ID
     * @param body 版本条件
     * @return 版本
     */
    @PostExchange("/versions/{versionId}/reject")
    CommercialTermVersionResponseBody rejectVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") String versionId, @RequestBody CommercialTermVersionCommandRequestBody body);

    /**
     * 排期并执行激活校验
     *
     * @param actorId 操作者
     * @param versionId 版本 ID
     * @param body 版本条件与生效起点
     * @return 版本
     */
    @PostExchange("/versions/{versionId}/schedule")
    CommercialTermVersionResponseBody scheduleVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") String versionId, @RequestBody ScheduleCommercialTermVersionRequestBody body);

    /**
     * 手工激活已到期的排期版本
     *
     * @param actorId 操作者
     * @param versionId 版本 ID
     * @param body 版本条件
     * @return 版本
     */
    @PostExchange("/versions/{versionId}/activate")
    CommercialTermVersionResponseBody activateVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") String versionId, @RequestBody CommercialTermVersionCommandRequestBody body);
}
