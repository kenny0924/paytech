package com.paytech.compliance.integration.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.paytech.client.master.api.checkout.ClientMasterCheckoutApi;
import com.paytech.client.master.api.checkout.response.PartyProfilesResponseBody;
import com.paytech.compliance.domain.ScreeningSubject;
import com.paytech.compliance.integration.ScreeningSubjectDirectory;

/**
 * 经 client-master CM-13（GET /client-master/v1/party-profiles）读取筛查主体名称
 * <li>机构取法定名称与注册国；个人取拼接的名、中间名、姓，出生年份、国籍与居住国（去重）。缺少名称的资料不返回（由调用方终态失败转人工）。</li>
 * <li>名称只在内存中使用，不记录。每次最多 1000 个资料 ID。</li>
 */
public class ClientMasterScreeningSubjectDirectory implements ScreeningSubjectDirectory {
    /** 调用方服务名。 */
    static final String CALLER = "compliance-service";

    private final ClientMasterCheckoutApi api;

    /** 注入由 HttpServiceClientFactory 创建的 client-master 客户端。 */
    public ClientMasterScreeningSubjectDirectory(ClientMasterCheckoutApi api) {
        this.api = api;
    }

    /** 按资料 ID 读取筛查主体。 */
    @Override
    public Map<Long, ScreeningSubject> findByProfileIds(List<Long> profileIds) {
        Map<Long, ScreeningSubject> result = new HashMap<>();
        if (profileIds.isEmpty()) {
            return result;
        }
        PartyProfilesResponseBody response = api.partyProfiles(CALLER, profileIds.stream().map(Object::toString).toList());
        if (response == null || response.getItems() == null) {
            throw new IllegalStateException("client-master 返回的资料为空");
        }
        for (PartyProfilesResponseBody.PartyProfile profile : response.getItems()) {
            ScreeningSubject subject = subject(profile);
            if (subject != null) {
                result.put(subject.getProfileId(), subject);
            }
        }
        return result;
    }

    /** 资料转筛查主体；缺少名称返回 null。 */
    static ScreeningSubject subject(PartyProfilesResponseBody.PartyProfile profile) {
        long profileId = Long.parseLong(profile.getProfileId());
        if ("ORGANIZATION".equals(profile.getPartyType())) {
            return blank(profile.getLegalName()) ? null : new ScreeningSubject(profileId, "ORGANIZATION", profile.getLegalName(), null,
                countries(profile.getIncorporationCountry()));
        }
        String fullName = Stream.of(profile.getGivenName(), profile.getMiddleName(), profile.getFamilyName())
            .filter(part -> !blank(part)).collect(Collectors.joining(" "));
        if (fullName.isBlank()) {
            return null;
        }
        String birthYear = profile.getBirthDate() == null || profile.getBirthDate().length() < 4 ? null
            : profile.getBirthDate().substring(0, 4);
        return new ScreeningSubject(profileId, "PERSON", fullName, birthYear,
            countries(profile.getNationalityCountry(), profile.getResidenceCountry()));
    }

    /** 去空去重的国家列表。 */
    static List<String> countries(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (!blank(value) && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    /** 空白判断。 */
    static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
