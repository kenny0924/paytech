package com.paytech.client.master.api.entityadmin.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Partner 详情；角色修改后返回同一结构。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocalPartnerDetailResponseBody {
    /** Local Partner ID。 */
    private String localPartnerId;

    /** Local Partner 编号。 */
    private String partnerNo;

    /** 法律主体 Party ID。 */
    private String partyId;

    /** 法定名称；资料缺失时为 null。 */
    private String legalName;

    /** 状态 Code。 */
    private String status;

    /** 当前 ACTIVE 角色 Code，升序。 */
    private List<String> roles;

    /** 根修订号，修改角色时作为 expectedChangeVersion 回传；尚未建立审计根时为 1。 */
    private long changeVersion;

    /** 以 PARTNER 来源关系提供（含已结束）的经营主体，按 entityNo 与生效时间升序。 */
    private List<ProvidedEntity> providedEntities;

    /** 一个提供关系。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProvidedEntity {
        /** OE 编号。 */
        private String entityNo;

        /** OE 法定名称；资料缺失时为 null。 */
        private String legalName;

        /** OE 状态 Code。 */
        private String entityStatus;

        /** 来源关系 ID。 */
        private String sourceRelationId;

        /** 来源关系状态 Code：ACTIVE 或 ENDED。 */
        private String sourceStatus;

        /** 生效开始时间。 */
        private String effectiveFrom;

        /** 生效结束时间；不限期为 null。 */
        private String effectiveTo;
    }
}
