package com.paytech.client.master.test.mocker.commercial;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.api.agreement.response.AgreementPartyUsageResponseBody;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * commercial 合同参与方引用查询的 DTO 配置与调用记录。
 * <li>未配置时 Mock 控制器返回 null，转为意外调用；全局 BeforeEach 配置“没有任何引用”的公共基线。</li>
 */
@TestComponent
public class CommercialAgreementPartyMocker implements Clear {
    private Boolean available;
    private final Set<Long> referencedParties = new TreeSet<>();
    private final Set<Long> referencedProfiles = new TreeSet<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();

    /** 公共基线：commercial 可用且没有任何合同引用。 */
    public synchronized void mockNoReferences() {
        available = true;
        referencedParties.clear();
        referencedProfiles.clear();
    }

    /** 某 Party 已被合同参与方引用。 */
    public synchronized void mockReferencedParty(long partyId) {
        available = true;
        referencedParties.add(partyId);
    }

    /** commercial 断连。 */
    public synchronized void mockUnavailable() {
        available = false;
    }

    /** 当前配置下 commercial 是否可用；未配置为 null。 */
    public synchronized Boolean available() {
        return available;
    }

    /**
     * 按请求返回被引用的子集。
     *
     * @param partyIds 请求的 Party ID
     * @param profileIds 请求的资料 ID
     * @return 响应 DTO；未配置时为 null
     */
    public synchronized AgreementPartyUsageResponseBody usage(List<String> partyIds, List<String> profileIds) {
        calls.add("exists:" + partyIds + ":" + profileIds);
        if (available == null) {
            return null;
        }
        List<String> parties = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        for (String id : partyIds == null ? List.<String>of() : partyIds) {
            if (referencedParties.contains(Long.parseLong(id))) {
                parties.add(id);
            }
        }
        for (String id : profileIds == null ? List.<String>of() : profileIds) {
            if (referencedProfiles.contains(Long.parseLong(id))) {
                profiles.add(id);
            }
        }
        return new AgreementPartyUsageResponseBody(parties, profiles);
    }

    /** 调用次数。 */
    public long count() {
        return calls.size();
    }

    /** 每例清空配置与记录。 */
    @Override
    public synchronized void clear() {
        available = null;
        referencedParties.clear();
        referencedProfiles.clear();
        calls.clear();
    }
}
