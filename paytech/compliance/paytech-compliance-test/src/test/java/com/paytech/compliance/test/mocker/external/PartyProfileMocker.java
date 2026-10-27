package com.paytech.compliance.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * client-master CM-13 资料读取的外部 Mocker：按资料 ID 配置筛查所需名称，记录调用；未配置的 ID 不返回（与 Owner 合同一致）
 * <li>可配置整体故障；记录每次请求的 ID 集合与调用方。</li>
 */
@TestComponent
public class PartyProfileMocker implements Clear {
    private final Map<String, PartyProfilesResponseBody.PartyProfile> profiles = new HashMap<>();

    private final List<List<String>> calls = new ArrayList<>();

    private final List<String> callers = new ArrayList<>();

    private final List<String> unexpected = new ArrayList<>();

    private MockHttpFault fault;

    /** 配置一个个人资料。 */
    public synchronized void mockPerson(long profileId, String givenName, String familyName, String birthDate, String nationality) {
        mockPerson(profileId, givenName, familyName, birthDate, nationality, null);
    }

    /** 配置一个带居住国的个人资料。 */
    public synchronized void mockPerson(long profileId, String givenName, String familyName, String birthDate, String nationality,
        String residence) {
        PartyProfilesResponseBody.PartyProfile profile = base(profileId);
        profile.setResidenceCountry(residence);
        profile.setPartyType("INDIVIDUAL");
        profile.setGivenName(givenName);
        profile.setFamilyName(familyName);
        profile.setBirthDate(birthDate);
        profile.setNationalityCountry(nationality);
        profiles.put(Long.toString(profileId), profile);
    }

    /** 配置一个机构资料。 */
    public synchronized void mockOrganization(long profileId, String legalName) {
        mockOrganization(profileId, legalName, null);
    }

    /** 配置一个带注册国的机构资料。 */
    public synchronized void mockOrganization(long profileId, String legalName, String incorporationCountry) {
        PartyProfilesResponseBody.PartyProfile profile = base(profileId);
        profile.setIncorporationCountry(incorporationCountry);
        profile.setPartyType("ORGANIZATION");
        profile.setLegalName(legalName);
        profiles.put(Long.toString(profileId), profile);
    }

    /** 配置整体故障。 */
    public synchronized void mockFault(MockHttpFault configured) {
        fault = configured;
    }

    /** 由 Mock Controller 调用：记录并返回已配置的资料；完全未配置记为意外。 */
    public synchronized PartyProfilesResponseBody answer(String caller, List<String> ids) {
        calls.add(List.copyOf(ids));
        callers.add(caller);
        if (fault != null) {
            throw fault;
        }
        if (profiles.isEmpty()) {
            throw unexpectedCall("party-profiles " + ids);
        }
        PartyProfilesResponseBody body = new PartyProfilesResponseBody();
        body.setItems(ids.stream().filter(profiles::containsKey).map(profiles::get).toList());
        return body;
    }

    /** 记录一次不在配置范围内的调用。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected.add(operation);
        return ScreeningMocker.problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    /** 调用记录（每次请求的资料 ID）。 */
    public synchronized List<List<String>> calls() {
        return List.copyOf(calls);
    }

    /** 调用方头记录。 */
    public synchronized List<String> callers() {
        return List.copyOf(callers);
    }

    /** 全局 AfterEach：没有意外调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(List.of(), unexpected, "存在未配置的 client-master 调用");
    }

    /** 全局 BeforeEach：空基线。 */
    public synchronized void assertEmpty() {
        assertAll("PartyProfileMocker 应处于空基线", () -> assertTrue(profiles.isEmpty()), () -> assertTrue(calls.isEmpty()),
            () -> assertTrue(unexpected.isEmpty()), () -> assertTrue(fault == null));
    }

    /** 每例清除。 */
    @Override
    public synchronized void clear() {
        profiles.clear();
        calls.clear();
        callers.clear();
        unexpected.clear();
        fault = null;
    }

    /** 资料 ID 与 Party ID 相同编码的基础资料。 */
    static PartyProfilesResponseBody.PartyProfile base(long profileId) {
        PartyProfilesResponseBody.PartyProfile profile = new PartyProfilesResponseBody.PartyProfile();
        profile.setProfileId(Long.toString(profileId));
        profile.setPartyId(Long.toString(profileId + 1));
        return profile;
    }
}
