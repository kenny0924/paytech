package com.paytech.client.master.ext.rest;

import static com.paytech.client.master.api.enums.CheckoutCandidateErrorCode.CLIENT_NOT_FOUND;
import static com.paytech.client.master.api.enums.CheckoutCandidateErrorCode.INVALID_CANDIDATE_QUERY;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.checkout.ClientMasterCheckoutApi;
import com.paytech.client.master.api.checkout.request.CheckoutCandidatesRequestBody;
import com.paytech.client.master.api.checkout.request.VerifyCheckoutCandidateRequestBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidateVerificationResponseBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody;
import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;
import com.paytech.client.master.domainservice.service.CheckoutCandidateQueryService;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/**
 * 两段式 Checkout 解析的客户主档入站（design/06 §2.1）；只做协议校验与转换，委托只读 Domain Service。
 * <li>私网调用范围仅 commercial-service；Caller Header 只防止调用范围误用，实际身份由部署网络保证。</li>
 */
@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@AllArgsConstructor
public class CheckoutCandidateController implements ClientMasterCheckoutApi {
    static final String COMMERCIAL = "commercial-service";
    /** compliance 读取参与方姓名用于名单筛查（CM-13）。 */
    static final String COMPLIANCE = "compliance-service";
    private static final int MAX_PROFILE_IDS = 1000;

    private final CheckoutCandidateQueryService service;

    /** 校验调用范围与查询格式后构造有序候选。 */
    @Override
    public CheckoutCandidatesResponseBody candidates(String callerService, CheckoutCandidatesRequestBody request) {
        authorize(callerService);
        return CheckoutCandidateConverter.response(service.candidates(CheckoutCandidateConverter.query(request)));
    }

    /** 新快照复核首次结果；变化以 unchanged=false 返回，不使用错误码表达。 */
    @Override
    public CheckoutCandidateVerificationResponseBody verify(String callerService, VerifyCheckoutCandidateRequestBody request) {
        authorize(callerService);
        List<String> changes = service.verify(CheckoutCandidateConverter.verification(request));
        return new CheckoutCandidateVerificationResponseBody(changes.isEmpty(), changes);
    }

    /** 按 ID 读取签署校验需要的参与方资料。 */
    @Override
    public PartyProfilesResponseBody partyProfiles(String callerService, List<String> ids) {
        if (!COMPLIANCE.equals(callerService)) {
            authorize(callerService);
        }
        return CheckoutCandidateConverter.profiles(service.partyProfiles(profileIds(ids)));
    }

    /** 1 到 1000 个规范正十进制 ID，重复值去重。 */
    private static Set<Long> profileIds(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_PROFILE_IDS) {
            throw new FailException(INVALID_CANDIDATE_QUERY);
        }
        Set<Long> result = new LinkedHashSet<>();
        for (String id : ids) {
            result.add(CheckoutCandidateConverter.positiveId(id));
        }
        return result;
    }

    /** 非 commercial 调用与不存在的 Client 使用同一拒绝合同。 */
    private static void authorize(String caller) {
        if (!COMMERCIAL.equals(caller)) {
            throw new FailException(CLIENT_NOT_FOUND);
        }
    }
}
