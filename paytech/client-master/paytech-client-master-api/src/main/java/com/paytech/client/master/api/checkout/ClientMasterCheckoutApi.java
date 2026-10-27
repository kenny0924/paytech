package com.paytech.client.master.api.checkout;

import java.util.List;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.client.master.api.checkout.request.CheckoutCandidatesRequestBody;
import com.paytech.client.master.api.checkout.request.VerifyCheckoutCandidateRequestBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidateVerificationResponseBody;
import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;

import jakarta.validation.Valid;

/**
 * commercial 两段式 Checkout 解析使用的客户主档只读能力（design/06 §2.1）。
 * <li>只读本库；不保存解析结果，不调用外部服务。</li>
 * <li>调用方仅允许 commercial-service；其他调用方与不存在的 Client 使用同一 404 合同。</li>
 */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface ClientMasterCheckoutApi {
    /**
     * 按 Affiliation 优先级返回只依赖主档事实即通过的 OE 候选及相关根版本。
     *
     * @param callerService 私网调用范围；仅允许 commercial-service，此 Header 本身不构成身份认证
     * @param request Client、消费者国家、币种、法律 Locale 与商业时点
     * @return Client 摘要、有序候选、商务查询范围与根版本；没有候选时 candidates 为空数组
     */
    @PostExchange("/client-master/v1/checkout-candidates")
    CheckoutCandidatesResponseBody candidates(@RequestHeader(value = "X-Caller-Service", required = false) String callerService,
        @Valid @RequestBody CheckoutCandidatesRequestBody request);

    /**
     * 外部资格检查后复核首次候选查询的根版本与选定候选。
     *
     * @param callerService 私网调用范围；仅允许 commercial-service
     * @param request 首次查询条件、首次返回的根版本与选定候选身份
     * @return unchanged 为 true 表示可继续；false 时列出变化的根
     */
    @PostExchange("/client-master/v1/checkout-candidates/verify")
    CheckoutCandidateVerificationResponseBody verify(
        @RequestHeader(value = "X-Caller-Service", required = false) String callerService,
        @Valid @RequestBody VerifyCheckoutCandidateRequestBody request);

    /**
     * 按 ID 读取合同签署校验需要的参与方资料。
     *
     * @param callerService 私网调用范围；允许 commercial-service（合同签署校验）与 compliance-service（名单筛查读取姓名）
     * @param ids 参与方资料 ID，规范正十进制 BIGINT 字符串，1 到 1000 个
     * @return 存在的资料，按 profileId 升序；不存在的 ID 不返回
     */
    @GetExchange("/client-master/v1/party-profiles")
    PartyProfilesResponseBody partyProfiles(@RequestHeader(value = "X-Caller-Service", required = false) String callerService,
        @RequestParam("ids") List<String> ids);
}
