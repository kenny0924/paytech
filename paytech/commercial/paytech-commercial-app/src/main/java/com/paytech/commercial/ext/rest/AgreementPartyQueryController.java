package com.paytech.commercial.ext.rest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.agreement.AgreementPartyQueryApi;
import com.paytech.commercial.api.agreement.response.AgreementPartyUsageResponseBody;
import com.paytech.commercial.domain.AgreementPartyUsage;
import com.paytech.commercial.domainservice.service.AgreementPartyQueryService;
import com.paytech.foundation.common.error.InvalidParameterException;

import lombok.AllArgsConstructor;

/** 合同参与方引用查询入站（design/06 §2.2）；仅 client-master-service 调用。 */
@RestController
@AllArgsConstructor
public class AgreementPartyQueryController implements AgreementPartyQueryApi {
    static final String CLIENT_MASTER = "client-master-service";
    private static final int MAX_IDS = 1000;

    private final AgreementPartyQueryService service;

    /** 校验调用范围与 ID 后返回被引用的子集。 */
    @Override
    public AgreementPartyUsageResponseBody exists(String callerService, List<String> partyIds, List<String> partyProfileIds) {
        if (!CLIENT_MASTER.equals(callerService)) {
            throw new InvalidParameterException("调用方不允许查询合同参与方");
        }
        Set<Long> parties = ids(partyIds);
        Set<Long> profiles = ids(partyProfileIds);
        if ((parties.isEmpty() && profiles.isEmpty()) || parties.size() + profiles.size() > MAX_IDS) {
            throw new InvalidParameterException("partyIds 与 partyProfileIds 合计必须为 1 到 1000 个");
        }
        AgreementPartyUsage usage = service.referenced(parties, profiles);
        return new AgreementPartyUsageResponseBody(text(usage.partyIds()), text(usage.partyProfileIds()));
    }

    /** 规范正十进制 BIGINT，去重；缺省为空。 */
    private static Set<Long> ids(List<String> values) {
        Set<Long> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
                throw new InvalidParameterException("ID 必须是规范正十进制 BIGINT");
            }
            try {
                result.add(Long.parseLong(value));
            } catch (NumberFormatException exception) {
                throw new InvalidParameterException("ID 必须是规范正十进制 BIGINT");
            }
        }
        return result;
    }

    /** 升序十进制文本。 */
    private static List<String> text(Set<Long> values) {
        List<String> result = new ArrayList<>();
        values.stream().sorted().forEach(value -> result.add(value.toString()));
        return result;
    }
}
