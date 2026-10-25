package com.paytech.commercial.api.term;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.commercial.api.term.request.CommercialTermVersionCommandRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermPlanRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermTemplateRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermVersionRequestBody;
import com.paytech.commercial.api.term.request.FillCommercialTermVersionFromTemplateRequestBody;
import com.paytech.commercial.api.term.request.RecordTermVersionAcceptanceRequestBody;
import com.paytech.commercial.api.term.request.ReplaceCommercialTermPricesRequestBody;
import com.paytech.commercial.api.term.request.ScheduleCommercialTermVersionRequestBody;
import com.paytech.commercial.api.term.response.CommercialTermPlanResponseBody;
import com.paytech.commercial.api.term.response.CommercialTermVersionResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 商业条款方案与版本的运营写入（technique.md §2.1–§2.3、§2.6）；私网，调用方 operations-console-bff
 * <li>版本状态机：DRAFT → PENDING_APPROVAL（提交，Maker）→ APPROVED（批准，Checker ≠ Maker）/ DRAFT（驳回）→（合同方案非免签版本）
 * CLIENT_SIGNED（登记客户签署）→ SCHEDULED（排期，执行激活校验）→ ACTIVE（激活，前一 ACTIVE 成为 SUPERSEDED）。</li>
 * <li>标准价目模板（owner_type=PLATFORM_TEMPLATE）复用版本与价格接口：只做结构校验，无合同、相关性、完整性与签署要求，不参与运行时取价。</li>
 * <li>每次写入携带方案 BCR 根修订 expectedChangeVersion 做 CAS，冲突 409；业务检查失败 400 BUSINESS_CHECK_FAILED。</li>
 * <li>本地 Maker-Checker 只记录"人已同意"，排期与激活时重新执行全部校验；操作者取 Gateway 传入、BFF 透传的
 * {@value #ACTOR_HEADER} 请求头（规范正十进制 Account ID），缺失或格式不符 400。</li>
 */
@HttpExchange(url = "/commercial-terms/v1", accept = "application/json")
public interface CommercialTermApi {
    /** Gateway 传入、BFF 透传的操作者 Account ID 请求头。 */
    String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /**
     * 创建条款方案（DRAFT）
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param body 条款类型、合同、Owner 与方案编号
     * @return 新建 201；同条款类型同编号且内容一致返回原方案 200；同编号内容不同 409；合同已有该条款类型方案、合同或 Owner 不符 400
     */
    @PostExchange("/plans")
    ResponseEntity<CommercialTermPlanResponseBody> createPlan(@RequestHeader(ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreateCommercialTermPlanRequestBody body);

    /**
     * 创建平台标准价目模板方案（DRAFT）
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param body 方案编号、模板名、条款类型与 OE
     * @return 新建 201；同条款类型同编号且内容一致返回原方案 200；同编号内容不同 409；非 Client 条款类型或 OE 不存在 400
     */
    @PostExchange("/templates")
    ResponseEntity<CommercialTermPlanResponseBody> createTemplate(@RequestHeader(ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreateCommercialTermTemplateRequestBody body);

    /**
     * 创建 DRAFT 版本：复制已有版本、基于标准价目模板版本生成，或空白
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param planId 方案 ID
     * @param body 根修订、复制来源或来源模板版本与版本属性
     * @return 201 与版本；根修订冲突 409；方案不存在、来源版本不属于该方案、复制与模板同时给出或模板版本不可用 400
     */
    @PostExchange("/plans/{planId}/versions")
    ResponseEntity<CommercialTermVersionResponseBody> createVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("planId") @Pattern(regexp = "[1-9][0-9]{0,18}") String planId,
        @Valid @RequestBody CreateCommercialTermVersionRequestBody body);

    /**
     * 整体替换 DRAFT 版本的价格集合
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param versionId 版本 ID
     * @param body 根修订与完整价格集合
     * @return 200 与版本；非 DRAFT、OE 不被合同覆盖、画像不存在、计费事件未登记、组件矩阵或结算字段不符、平台服务费价目的 Partner 合同
     *         OE 法律主体不等于 Local Partner 法律主体 400；根修订冲突 409
     */
    @PutExchange("/versions/{versionId}/prices")
    CommercialTermVersionResponseBody replacePrices(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
        @Valid @RequestBody ReplaceCommercialTermPricesRequestBody body);

    /**
     * 从标准价目模板版本补齐 DRAFT 合同版本：只追加缺失的（OE × 币种 × 画像）价格行，规则同基于模板生成
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param versionId 版本 ID
     * @param body 根修订与模板版本
     * @return 200 与版本；非 DRAFT、模板方案或模板版本不可用 400；根修订冲突 409
     */
    @PostExchange("/versions/{versionId}/fill-from-template")
    CommercialTermVersionResponseBody fillFromTemplate(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
        @Valid @RequestBody FillCommercialTermVersionFromTemplateRequestBody body);

    /**
     * 提交审批：DRAFT → PENDING_APPROVAL，操作人记为 Maker
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param versionId 版本 ID
     * @param body 根修订
     * @return 200 与版本；非 DRAFT 或没有价格 400；根修订冲突 409
     */
    @PostExchange("/versions/{versionId}/submit")
    CommercialTermVersionResponseBody submitVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
        @Valid @RequestBody CommercialTermVersionCommandRequestBody body);

    /**
     * 批准：PENDING_APPROVAL → APPROVED，操作人为 Checker
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param versionId 版本 ID
     * @param body 根修订
     * @return 200 与版本；非 PENDING_APPROVAL 或 Checker 等于 Maker 400；根修订冲突 409
     */
    @PostExchange("/versions/{versionId}/approve")
    CommercialTermVersionResponseBody approveVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
        @Valid @RequestBody CommercialTermVersionCommandRequestBody body);

    /**
     * 驳回：PENDING_APPROVAL → DRAFT，操作人为 Checker
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param versionId 版本 ID
     * @param body 根修订
     * @return 200 与版本；非 PENDING_APPROVAL 或 Checker 等于 Maker 400；根修订冲突 409
     */
    @PostExchange("/versions/{versionId}/reject")
    CommercialTermVersionResponseBody rejectVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
        @Valid @RequestBody CommercialTermVersionCommandRequestBody body);

    /**
     * 登记客户签署：APPROVED → CLIENT_SIGNED（仅合同方案）
     *
     * @param actorId 登记签署证据的操作者 Account ID
     * @param versionId 版本 ID
     * @param body 根修订、签署 Party、方式、时刻、编号与价格附件
     * @return 200 与版本；非 APPROVED、模板版本、Party 不是合同对应角色 400；同编号同内容重放返回当前结果，不同内容 409
     */
    @PostExchange("/versions/{versionId}/acceptance")
    CommercialTermVersionResponseBody recordAcceptance(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
        @Valid @RequestBody RecordTermVersionAcceptanceRequestBody body);

    /**
     * 排期：APPROVED（模板或免签版本）/ CLIENT_SIGNED（合同方案）→ SCHEDULED，写入生效起点并执行激活校验
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param versionId 版本 ID
     * @param body 根修订与生效起点
     * @return 200 与版本；状态不符、价格协议尚未签署或任一激活校验失败 400；根修订冲突 409
     */
    @PostExchange("/versions/{versionId}/schedule")
    CommercialTermVersionResponseBody scheduleVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
        @Valid @RequestBody ScheduleCommercialTermVersionRequestBody body);

    /**
     * 激活：SCHEDULED → ACTIVE（生效起点已到），前一 ACTIVE → SUPERSEDED 并写其生效终点；提交后发布 CommercialTermVersionActivated
     * <li>与定时作业 commercialTermVersionActivation 执行同一用例；作业不可用时由本接口手工触发。</li>
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param versionId 版本 ID
     * @param body 根修订
     * @return 200 与版本；非 SCHEDULED、生效起点未到或任一激活校验失败 400；根修订冲突 409
     */
    @PostExchange("/versions/{versionId}/activate")
    CommercialTermVersionResponseBody activateVersion(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
        @Valid @RequestBody CommercialTermVersionCommandRequestBody body);

    /**
     * 版本详情
     *
     * @param versionId 版本 ID
     * @return 200 与版本；不存在 404
     */
    @GetExchange("/versions/{versionId}")
    CommercialTermVersionResponseBody getVersion(
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId);
}
