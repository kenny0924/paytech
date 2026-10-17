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
    }
}
