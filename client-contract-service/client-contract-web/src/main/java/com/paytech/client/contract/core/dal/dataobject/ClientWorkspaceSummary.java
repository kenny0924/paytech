package com.paytech.client.contract.core.dal.dataobject;

import java.time.Instant;

import com.paytech.client.contract.domain.enums.ClientStatus;
import com.paytech.client.contract.domain.enums.ClientType;

import lombok.Getter;
import lombok.Setter;

/** 已批准 Client 的工作区展示摘要（t_client、t_party、资料与申请的只读组合）；不含证件、地址或联系方式。 */
@Getter
@Setter
public class ClientWorkspaceSummary {
    /** Client ID。 */
    private long clientId;
    /** Client 编号。 */
    private String clientNo;
    /** 个人或组织。 */
    private ClientType clientType;
    /** CCT Client 生命周期状态。 */
    private ClientStatus clientStatus;
    /** 组织法定名称；个人为空。 */
    private String legalName;
    /** 个人名；组织为空。 */
    private String givenName;
    /** 个人姓；组织为空。 */
    private String familyName;
    /** 组织注册国。 */
    private String incorporationCountry;
    /** 个人居住国。 */
    private String residenceCountry;
    /** 经营模式代码。 */
    private String businessModelCode;
    /** 最近一份 APPROVED 申请的结论时间（该申请结论后不再更新，取其 update_time）。 */
    private Instant approvedAt;

    /** 组织取法定名称，个人取名与姓；都缺失时为空。 */
    public String displayName() {
        if (clientType == ClientType.ORGANIZATION) {
            return blankToNull(legalName);
        }
        String joined = String.join(" ", java.util.stream.Stream.of(givenName, familyName)
            .filter(part -> part != null && !part.isBlank()).toList());
        return blankToNull(joined);
    }

    /** 组织取注册国，个人取居住国。 */
    public String country() {
        return clientType == ClientType.ORGANIZATION ? incorporationCountry : residenceCountry;
    }

    /** 空白文本按缺失处理。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
