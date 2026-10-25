package com.paytech.client.master.api.entityadmin.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Partner 列表中的一行。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocalPartnerSummaryResponseBody {
    /** Local Partner ID，十进制字符串。 */
    private String localPartnerId;

    /** Local Partner 编号。 */
    private String partnerNo;

    /** 法定名称；资料缺失时为 null。 */
    private String legalName;

    /** 状态 Code：PENDING、ACTIVE、SUSPENDED 或 TERMINATED。 */
    private String status;

    /** 当前 ACTIVE 角色 Code，升序。 */
    private List<String> roles;

    /** 以 ACTIVE PARTNER 来源关系提供的经营主体数。 */
    private int providedEntityCount;

    /** 根修订号，修改角色时作为 expectedChangeVersion 回传；尚未建立审计根时为 1。 */
    private long changeVersion;
}
