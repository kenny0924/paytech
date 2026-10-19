package com.paytech.commercial.api.agreement;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.commercial.api.agreement.request.AddAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody;
import com.paytech.commercial.api.agreement.request.CreateAgreementRequestBody;
import com.paytech.commercial.api.agreement.request.RecordAgreementAcceptanceRequestBody;
import com.paytech.commercial.api.agreement.request.ReplaceAgreementProductsRequestBody;
import com.paytech.commercial.api.agreement.request.TerminateAgreementRequestBody;
import com.paytech.commercial.api.agreement.request.UpdateAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.response.AgreementCapabilityResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementDetailResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementResponseBody;

import jakarta.validation.Valid;

/**
 * 合同、签署、订阅与开通项的运营写入合同（technique.md §2.2；仅私网，调用方 operations-console-bff）
 * <li>写入以合同 BCR Root 版本做 CAS：请求携带 expectedChangeVersion，版本不一致返回 409；写入不处理 Idempotency-Key。</li>
 * <li>业务检查失败返回 400 BUSINESS_CHECK_FAILED 与中文 detail，资源锁不可用返回 503。</li>
 * <li>只有激活与终止在提交后发布 AgreementStatusChanged。</li>
 */
@HttpExchange("/agreements/v1")
public interface AgreementApi {
    /** Gateway 传入、BFF 透传的操作者 Account ID 请求头。 */
    String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /**
     * 创建 DRAFT 合同，同时登记参与方角色、合同文件与 PARTNER 覆盖 OE
     *
     * @param actorId 操作者 Account ID，规范正十进制
     * @param body 合同内容
     * @return 合同写入结果；同 agreementNo 同内容返回原合同，不同内容返回 409
     */
    @PostExchange
    AgreementResponseBody createAgreement(@RequestHeader(ACTOR_HEADER) String actorId,
        @Valid @RequestBody CreateAgreementRequestBody body);

    /**
     * 合同详情：参与方、文件、覆盖 OE、订阅产品、开通项与当前版本
     *
     * @param agreementId 合同 ID
     * @return 合同详情；不存在返回 400
     */
    @GetExchange("/{agreementId}")
    AgreementDetailResponseBody getAgreement(@PathVariable("agreementId") String agreementId);

    /**
     * DRAFT → PENDING_SIGNATURE：要求恰好一份 SIGNING_DOCUMENT，PARTNER 合同至少覆盖一个 OE
     *
     * @param actorId 操作者 Account ID
     * @param agreementId 合同 ID
     * @param body 版本条件
     * @return 合同写入结果；已是 PENDING_SIGNATURE 且版本为期望版本加一时按重放返回当前结果
     */
    @PostExchange("/{agreementId}/submit-for-signature")
    AgreementResponseBody submitForSignature(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("agreementId") String agreementId, @Valid @RequestBody AgreementTransitionRequestBody body);

    /**
     * 登记一个 Party 全部角色的签署结果；全部 Party 签完后合同进入 SIGNED
     *
     * @param actorId 登记签署证据的操作者 Account ID
     * @param agreementId 合同 ID
     * @param partyId 法律 Party ID
     * @param body 签署结果
     * @return 合同写入结果；同 acceptanceReference 同内容按重放返回当前结果
     */
    @PostExchange("/{agreementId}/parties/{partyId}/acceptance")
    AgreementResponseBody recordAcceptance(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("agreementId") String agreementId, @PathVariable("partyId") String partyId,
        @Valid @RequestBody RecordAgreementAcceptanceRequestBody body);

    /**
     * SIGNED → ACTIVE，写入激活时刻；提交后发布 AgreementStatusChanged
     *
     * @param actorId 操作者 Account ID
     * @param agreementId 合同 ID
     * @param body 版本条件
     * @return 合同写入结果；已是 ACTIVE 且版本为期望版本加一时按重放返回当前结果
     */
    @PostExchange("/{agreementId}/activate")
    AgreementResponseBody activate(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("agreementId") String agreementId, @Valid @RequestBody AgreementTransitionRequestBody body);

    /**
     * ACTIVE → TERMINATED，写入生效终点与终止通知文件；提交后发布 AgreementStatusChanged
     *
     * @param actorId 操作者 Account ID
     * @param agreementId 合同 ID
     * @param body 终止内容与版本条件
     * @return 合同写入结果；已是 TERMINATED 且版本为期望版本加一时按重放返回当前结果
     */
    @PostExchange("/{agreementId}/terminate")
    AgreementResponseBody terminate(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("agreementId") String agreementId, @Valid @RequestBody TerminateAgreementRequestBody body);

    /**
     * 整体替换订阅产品集合；集合等价时不产生变更并返回当前版本
     *
     * @param actorId 操作者 Account ID
     * @param agreementId 合同 ID
     * @param body 产品集合与版本条件
     * @return 合同写入结果
     */
    @PutExchange("/{agreementId}/products")
    AgreementResponseBody replaceProducts(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("agreementId") String agreementId, @Valid @RequestBody ReplaceAgreementProductsRequestBody body);

    /**
     * 新增开通项（ENABLED）及其约束行
     *
     * @param actorId 操作者 Account ID
     * @param agreementId 合同 ID
     * @param body 开通项内容与版本条件
     * @return 开通项写入结果；同 (entityNo, capabilityCode) 已存在返回 409
     */
    @PostExchange("/{agreementId}/capabilities")
    AgreementCapabilityResponseBody addCapability(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("agreementId") String agreementId, @Valid @RequestBody AddAgreementCapabilityRequestBody body);

    /**
     * 整体改写开通项状态、区间、保证金与约束行；内容不变时不产生变更并返回当前版本
     *
     * @param actorId 操作者 Account ID
     * @param agreementId 合同 ID
     * @param agreementCapabilityId 开通项 ID
     * @param body 开通项内容与版本条件
     * @return 开通项写入结果
     */
    @PutExchange("/{agreementId}/capabilities/{agreementCapabilityId}")
    AgreementCapabilityResponseBody updateCapability(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("agreementId") String agreementId,
        @PathVariable("agreementCapabilityId") String agreementCapabilityId,
        @Valid @RequestBody UpdateAgreementCapabilityRequestBody body);
}
