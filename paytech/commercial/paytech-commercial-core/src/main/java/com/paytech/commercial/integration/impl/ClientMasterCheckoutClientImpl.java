package com.paytech.commercial.integration.impl;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import com.paytech.client.master.api.checkout.ClientMasterCheckoutApi;
import com.paytech.client.master.api.checkout.request.CheckoutCandidatesRequestBody;
import com.paytech.client.master.api.checkout.request.VerifyCheckoutCandidateRequestBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody;
import com.paytech.client.master.api.checkout.response.CheckoutCandidatesResponseBody.RootVersions;
import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;
import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.commercial.domain.*;
import com.paytech.commercial.integration.ClientMasterCheckoutClient;
import com.paytech.commercial.domain.ClientMasterCandidates.*;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/**
 * 经 client-master-api 读取候选、复核与参与方资料（design/06 §2.1、§4）。
 * <li>失败关闭：client-master 不可用、返回未登记错误或响应不合法时一律 OWNER_UNAVAILABLE，不降级、不重试。</li>
 */
@AllArgsConstructor
public class ClientMasterCheckoutClientImpl implements ClientMasterCheckoutClient {
    static final String CALLER = "commercial-service";

    private final ClientMasterCheckoutApi api;

    /** 首次候选查询。 */
    @Override
    public ClientMasterCandidates candidates(CheckoutQuery query) {
        CheckoutCandidatesResponseBody response = call(() -> api.candidates(CALLER, request(query)));
        return parse(() -> ClientMasterCandidateConverter.candidates(response));
    }

    /** 复核时原样回传首次根版本与选定授权身份。 */
    @Override
    public boolean unchanged(CheckoutQuery query, ClientMasterCandidates master, CheckoutCandidateContext selected) {
        var body = new VerifyCheckoutCandidateRequestBody();
        body.setQuery(request(query));
        body.setRootVersions(rootVersions(master));
        var candidate = new VerifyCheckoutCandidateRequestBody.SelectedCandidate();
        var eligibility = selected.eligibility();
        candidate.setEntityNo(eligibility.entityNo());
        candidate.setAffiliationId(Long.toString(eligibility.affiliationId()));
        candidate.setPriorityNo(eligibility.priorityNo());
        candidate.setScopeId(Long.toString(eligibility.scopeId()));
        candidate.setSourceRelationId(Long.toString(eligibility.sourceRelationId()));
        candidate.setCandidateDigest(eligibility.candidateDigest());
        body.setCandidate(candidate);
        var response = call(() -> api.verify(CALLER, body));
        return parse(() -> ClientMasterCandidateConverter.unchanged(response));
    }

    /** 按 ID 读取资料；返回不在请求范围内的资料视为响应不合法。 */
    @Override
    public List<PartyProfileFacts> partyProfiles(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> wire = ids.stream().map(Object::toString).toList();
        PartyProfilesResponseBody response = call(() -> api.partyProfiles(CALLER, wire));
        return parse(() -> ClientMasterCandidateConverter.profiles(response, new HashSet<>(ids)));
    }

    /** 查询条件逐值传给 client-master；商业时点保留原始文本。 */
    private static CheckoutCandidatesRequestBody request(CheckoutQuery query) {
        var body = new CheckoutCandidatesRequestBody();
        body.setClientId(Long.toString(query.clientId()));
        body.setConsumerCountryCode(query.country());
        body.setCurrency(query.currency());
        body.setLocale(query.locale());
        body.setContextAsOfTime(query.asOfText());
        return body;
    }

    /** 首次根版本按类型还原为 Wire 结构。 */
    private static RootVersions rootVersions(ClientMasterCandidates master) {
        Map<String, List<CheckoutCandidatesResponseBody.RootVersion>> groups = new HashMap<>();
        CheckoutCandidatesResponseBody.RootVersion client = null;
        for (RootVersion root : master.roots().values()) {
            var wire = new CheckoutCandidatesResponseBody.RootVersion(root.recordId(), Long.toString(root.historyId()),
                root.version());
            if ("CLIENT".equals(root.type())) {
                client = wire;
            } else {
                groups.computeIfAbsent(root.type(), ignored -> new ArrayList<>()).add(wire);
            }
        }
        return new RootVersions(client, sorted(groups.get("OPERATING_ENTITY")), sorted(groups.get("LOCAL_PARTNER")),
            sorted(groups.get("CONSUMER_LEGAL_DOCUMENT")), sorted(groups.get("SELLER_DISCLOSURE")));
    }

    /** 按记录 ID 排序，缺失视为空数组。 */
    private static List<CheckoutCandidatesResponseBody.RootVersion> sorted(
        List<CheckoutCandidatesResponseBody.RootVersion> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().sorted(Comparator.comparing(CheckoutCandidatesResponseBody.RootVersion::getRecordId))
            .toList();
    }

    /**
     * 远程调用的错误边界：只把 client-master 登记的三类业务错误映射为原合同错误，其他一律失败关闭。
     */
    private static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (FailException failure) {
            throw translate(failure);
        } catch (RuntimeException failure) {
            throw new ErrorException(OWNER_UNAVAILABLE, "client-master 不可用", failure);
        }
    }

    /** 登记错误码到原 Checkout 合同错误的映射。 */
    private static RuntimeException translate(FailException failure) {
        String code = failure.getCode();
        if (CheckoutCandidateErrorCode.CLIENT_NOT_FOUND.getCode().equals(code)) {
            return new FailException(CLIENT_NOT_FOUND, null, failure);
        }
        if (CheckoutCandidateErrorCode.CLIENT_NOT_ELIGIBLE.getCode().equals(code)) {
            return new FailException(CLIENT_NOT_ELIGIBLE, null, failure);
        }
        if (CheckoutCandidateErrorCode.CANDIDATE_CONTEXT_AMBIGUOUS.getCode().equals(code)) {
            return new FailException(CCT_CONTEXT_AMBIGUOUS, null, failure);
        }
        return new ErrorException(OWNER_UNAVAILABLE, "client-master 返回未处理的错误", failure);
    }

    /** 响应解析失败表示合同不一致，失败关闭。 */
    private static <T> T parse(Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException invalid) {
            throw new ErrorException(OWNER_UNAVAILABLE, "client-master 响应不符合合同", invalid);
        }
    }

    /** 时间解析，空值保持 null。 */
    static Instant time(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
