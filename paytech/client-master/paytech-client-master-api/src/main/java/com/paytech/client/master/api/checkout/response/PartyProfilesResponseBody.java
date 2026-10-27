package com.paytech.client.master.api.checkout.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 合同签署校验读取的参与方资料。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartyProfilesResponseBody {
    /** 存在的资料，按 profileId 升序。 */
    private List<PartyProfile> items;

    /** 参与方资料摘要。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartyProfile {
        /** 资料 ID。 */
        private String profileId;

        /** 所属 Party ID。 */
        private String partyId;

        /** 首次批准时间；未批准为 null。 */
        private String approvedTime;

        /** 仍处于草稿时的申请 ID；已锁定为 null。 */
        private String draftApplicationId;

        /** 资料内容 SHA-256；可能为空。 */
        private String contentHash;

        /** Party 类型 INDIVIDUAL 或 ORGANIZATION。 */
        private String partyType;

        /** 个人名（拉丁字母），筛查用；机构为空。 */
        private String givenName;

        /** 个人中间名，可空。 */
        private String middleName;

        /** 个人姓，筛查用；机构为空。 */
        private String familyName;

        /** 个人出生日期 YYYY-MM-DD，筛查用；机构为空。 */
        private String birthDate;

        /** 个人国籍 ISO 3166-1 alpha-2，筛查用；机构为空。 */
        private String nationalityCountry;

        /** 个人居住国 ISO 3166-1 alpha-2，筛查用；机构为空。 */
        private String residenceCountry;

        /** 机构法定名称，筛查用；个人为空。 */
        private String legalName;

        /** 机构注册国 ISO 3166-1 alpha-2，筛查用；个人为空。 */
        private String incorporationCountry;
    }
}
