package com.paytech.client.master.domain.onboarding;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.client.master.core.dal.dataobject.ClientPersonIdentity;

/**
 * 人员是否已被合同参与方引用（原 ClientPeopleMapper 读取 t_agreement_party 的 EXISTS，现由 commercial 提供）。
 * <li>失败关闭：commercial 不可用、响应不合法或人员未在本次查询范围内时，一律视为已引用，禁止修改。</li>
 */
public final class ClientAgreementPartyUsage {
    private static final ClientAgreementPartyUsage UNAVAILABLE =
        new ClientAgreementPartyUsage(false, Set.of(), Set.of(), Set.of());

    private final boolean available;
    private final Set<String> queried;
    private final Set<Long> referencedPartyIds;
    private final Set<Long> referencedProfileIds;

    /** 只在本类工厂内构造，集合均为不可变副本。 */
    private ClientAgreementPartyUsage(boolean available, Set<String> queried, Set<Long> referencedPartyIds,
        Set<Long> referencedProfileIds) {
        this.available = available;
        this.queried = Set.copyOf(queried);
        this.referencedPartyIds = Set.copyOf(referencedPartyIds);
        this.referencedProfileIds = Set.copyOf(referencedProfileIds);
    }

    /**
     * 无法确认合同引用时的失败关闭结果。
     *
     * @return 任何人员都视为已引用
     */
    public static ClientAgreementPartyUsage unavailable() {
        return UNAVAILABLE;
    }

    /**
     * commercial 已确认的引用结果。
     *
     * @param persons 本次查询的人员
     * @param referencedPartyIds 被引用的 Party ID
     * @param referencedProfileIds 被引用的资料 ID
     * @return 只对本次查询的人员给出确定结论
     */
    public static ClientAgreementPartyUsage of(Collection<ClientPersonIdentity> persons, Set<Long> referencedPartyIds,
        Set<Long> referencedProfileIds) {
        Set<String> queried = persons.stream().map(ClientAgreementPartyUsage::key).collect(Collectors.toSet());
        return new ClientAgreementPartyUsage(true, queried, referencedPartyIds, referencedProfileIds);
    }

    /**
     * 人员是否已被合同引用。
     *
     * @param person 本库人员身份
     * @return 已引用、无法确认或未查询时为 true
     */
    public boolean shares(ClientPersonIdentity person) {
        if (!available || !queried.contains(key(person))) {
            return true;
        }
        return referencedPartyIds.contains(person.partyId()) || referencedProfileIds.contains(person.profileId());
    }

    /** 人员与其当前资料一起作为查询身份。 */
    private static String key(ClientPersonIdentity person) {
        return person.partyId() + ":" + person.profileId();
    }
}
