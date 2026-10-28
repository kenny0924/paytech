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
import com.paytech.commercial.api.agreement.request.ResumeAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.SuspendAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.request.TerminateAgreementRequestBody;
import com.paytech.commercial.api.agreement.request.UpdateAgreementCapabilityRequestBody;
import com.paytech.commercial.api.agreement.response.AgreementCapabilityResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementDetailResponseBody;
import com.paytech.commercial.api.agreement.response.AgreementResponseBody;
import com.paytech.commercial.api.agreement.response.CapabilityPrerequisiteCheckResponseBody;

import jakarta.validation.Valid;

/**
 * 合同、签署、订阅与开通项的运营写入合同（technique.md §2.2；仅私网，调用方 operations-console-bff）
 * <li>写入以合同 BCR Root 版本做 CAS：请求携带 expectedChangeVersion，版本不一致返回 409；写入不处理 Idempotency-Key。</li>
 * <li>业务检查失败返回 400 BUSINESS_CHECK_FAILED 与中文 detail，资源锁不可用返回 503。</li>
 * <li>只有激活与终止在提交后发布 AgreementStatusChanged；开通项执行状态变化在提交后发布 AgreementCapabilityStatusChanged。</li>
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
     *         <li>PARTNER 合同的 Local Partner 有平台服务费方案时，OE 法律主体不等于 Local Partner 法律主体 400。</li>
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
     * 新增开通项及其接受条件与 3DS 要求；成员资格 ACTIVE
     * <li>CLIENT 合同执行状态为 PENDING_PREREQUISITES，同事务登记前置检查，提交后由门禁核对；PARTNER 合同直接 ENABLED。</li>
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
     * 整体改写开通项成员资格、区间、保证金、接受条件与 3DS 要求；内容不变时不产生变更并返回当前版本
     * <li>关闭（DISABLED）清空执行状态；重新纳入（ACTIVE）时 CLIENT 合同回到 PENDING_PREREQUISITES 并登记前置检查。</li>
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

    /**
     * 运营手动暂停开通项：执行状态置 SUSPENDED_MANUALLY，前置门禁不再触碰
     *
     * @param actorId 操作者 Account ID
     * @param agreementId 合同 ID
     * @param agreementCapabilityId 开通项 ID
     * @param body 暂停原因与版本条件
     * @return 开通项写入结果
     *         <li>合同已结束、PARTNER 合同、开通项已关闭或已是手动暂停返回 400；版本不一致返回 409。</li>
     */
    @PostExchange("/{agreementId}/capabilities/{agreementCapabilityId}/suspension")
    AgreementCapabilityResponseBody suspendCapability(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("agreementId") String agreementId,
        @PathVariable("agreementCapabilityId") String agreementCapabilityId,
        @Valid @RequestBody SuspendAgreementCapabilityRequestBody body);

    /**
     * 运营恢复手动暂停的开通项：执行状态回到 PENDING_PREREQUISITES，同事务登记前置检查，提交后由门禁核对
     *
     * @param actorId 操作者 Account ID
     * @param agreementId 合同 ID
     * @param agreementCapabilityId 开通项 ID
     * @param body 版本条件
     * @return 开通项写入结果（executionStatus=PENDING_PREREQUISITES）
     *         <li>开通项不是手动暂停返回 400；版本不一致返回 409。</li>
     */
    @PostExchange("/{agreementId}/capabilities/{agreementCapabilityId}/resumption")
    AgreementCapabilityResponseBody resumeCapability(@RequestHeader(ACTOR_HEADER) String actorId,
        @PathVariable("agreementId") String agreementId,
        @PathVariable("agreementCapabilityId") String agreementCapabilityId,
        @Valid @RequestBody ResumeAgreementCapabilityRequestBody body);

    /**
     * 立即核对 CLIENT 合同全部开通项的前置（资格、合同与价格、渠道身份），必要时迁移执行状态并返回逐项结论
     * <li>可能迁移状态并推进合同版本，因此是 POST；不需要操作者，状态迁移以系统身份记录。</li>
     *
     * @param agreementId 合同 ID
     * @return 核对结果
     *         <li>合同不存在或为 PARTNER 合同返回 400；资格服务不可用时对应项为 UNKNOWN、状态不迁移，仍返回 200。</li>
     */
    @PostExchange("/{agreementId}/prerequisite-checks")
    CapabilityPrerequisiteCheckResponseBody checkPrerequisites(@PathVariable("agreementId") String agreementId);
}
