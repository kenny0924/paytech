package com.paytech.client.master.domain.checkout;

import java.time.Instant;
import java.time.LocalDate;

import com.paytech.client.master.domain.enums.ClientType;

import lombok.Getter;
import lombok.Setter;

/**
 * 参与方资料与筛查用姓名的组合查询结果（t_party_profile + t_party + 个人 / 机构明细）
 * <li>不含证件号、地址或联系方式；姓名、生日与国籍供 compliance 名单筛查，机构只给法定名称。</li>
 */
@Getter
@Setter
public class PartyProfileView {
    /** 资料 ID。 */
    private Long profileId;
    /** Party ID。 */
    private Long partyId;
    /** Party 类型。 */
    private ClientType partyType;
    /** 首次批准时间，可空。 */
    private Instant approvedTime;
    /** 草稿所属申请，可空。 */
    private Long draftApplicationId;
    /** 批准内容摘要，可空。 */
    private String contentHash;
    /** 个人名。 */
    private String givenName;
    /** 个人中间名。 */
    private String middleName;
    /** 个人姓。 */
    private String familyName;
    /** 个人出生日期。 */
    private LocalDate birthDate;
    /** 个人国籍。 */
    private String nationalityCountry;
    /** 个人居住国。 */
    private String residenceCountry;
    /** 机构法定名称。 */
    private String legalName;
    /** 机构注册国。 */
    private String incorporationCountry;

    /** 含个人资料，不输出到日志。 */
    @Override
    public String toString() {
        return "PartyProfileView[redacted]";
    }
}
