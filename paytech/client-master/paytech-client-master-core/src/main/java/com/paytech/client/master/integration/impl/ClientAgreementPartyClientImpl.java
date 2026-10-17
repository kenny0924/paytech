package com.paytech.client.master.integration.impl;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.client.master.core.dal.dataobject.ClientPersonIdentity;
import com.paytech.client.master.domain.onboarding.ClientAgreementPartyUsage;
import com.paytech.client.master.integration.ClientAgreementPartyClient;
import com.paytech.commercial.api.agreement.AgreementPartyQueryApi;
import com.paytech.commercial.api.agreement.response.AgreementPartyUsageResponseBody;

import lombok.AllArgsConstructor;

/**
 * 经 commercial-api 查询合同参与方引用（替代原 ClientPeopleMapper 对 t_agreement_party 的跨库读取）。
 * <li>失败关闭：任何调用失败或响应不合法都视为“已引用”，人员不可修改；只记录安全的失败类别，不记录响应内容。</li>
 */
@AllArgsConstructor
public class ClientAgreementPartyClientImpl implements ClientAgreementPartyClient {
    static final String CALLER = "client-master-service";
    private static final Logger LOG = LoggerFactory.getLogger(ClientAgreementPartyClientImpl.class);

    private final AgreementPartyQueryApi api;

    /** 一次查询全部人员的 Party 与资料 ID。 */
    @Override
    public ClientAgreementPartyUsage usage(Collection<ClientPersonIdentity> persons) {
        if (persons.isEmpty()) {
            return ClientAgreementPartyUsage.of(List.of(), Set.of(), Set.of());
        }
        List<String> partyIds = persons.stream().map(person -> Long.toString(person.partyId())).distinct().toList();
        List<String> profileIds = persons.stream().map(person -> Long.toString(person.profileId())).distinct().toList();
        AgreementPartyUsageResponseBody response;
        try {
            response = api.exists(CALLER, partyIds, profileIds);
        } catch (RuntimeException failure) {
            // 共享标记的唯一外部依赖：不可用时按“已引用”处理，禁止修改人员，而不是放行。
            LOG.warn("commercial 合同参与方查询不可用，人员按已引用处理: {}", failure.getClass().getSimpleName());
            return ClientAgreementPartyUsage.unavailable();
        }
        Set<Long> parties = ids(response == null ? null : response.getReferencedPartyIds(), partyIds);
        Set<Long> profiles = ids(response == null ? null : response.getReferencedPartyProfileIds(), profileIds);
        if (parties == null || profiles == null) {
            LOG.warn("commercial 合同参与方查询响应不合法，人员按已引用处理");
            return ClientAgreementPartyUsage.unavailable();
        }
        return ClientAgreementPartyUsage.of(persons, parties, profiles);
    }

    /** 解析被引用 ID；缺失、格式错误或不属于请求范围时返回 null。 */
    private static Set<Long> ids(List<String> values, List<String> requested) {
        if (values == null) {
            return null;
        }
        Set<Long> result = new TreeSet<>();
        for (String value : values) {
            if (value == null || !requested.contains(value)) {
                return null;
            }
            result.add(Long.parseLong(value));
        }
        return result;
    }
}
