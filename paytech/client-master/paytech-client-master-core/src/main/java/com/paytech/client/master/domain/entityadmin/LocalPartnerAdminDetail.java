package com.paytech.client.master.domain.entityadmin;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Local Partner 详情：组合行（含角色）与提供的经营主体。 */
@Getter
@AllArgsConstructor
public class LocalPartnerAdminDetail {
    /** 主档、角色、提供数与根修订号。 */
    private final LocalPartnerAdminView partner;
    /** 以 PARTNER 来源关系提供（含已结束）的经营主体。 */
    private final List<ProvidedEntityView> providedEntities;
}
