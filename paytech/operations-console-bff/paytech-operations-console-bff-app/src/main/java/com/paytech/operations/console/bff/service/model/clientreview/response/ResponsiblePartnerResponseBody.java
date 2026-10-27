package com.paytech.operations.console.bff.service.model.clientreview.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 可作为责任方 / 代操作对象的 PayFac：ACTIVE 且具有 OE_PROVIDER 角色的 Local Partner。 */
@Getter
@AllArgsConstructor
public class ResponsiblePartnerResponseBody {

    /** Local Partner ID */
    private final String localPartnerId;

    /** Partner 业务编号 */
    private final String partnerNo;

    /**
     * 法定名称
     * <li>client-master 未登记时为 null，页面以编号展示。</li>
     */
    private final String legalName;

    /** Partner 状态，当前恒为 ACTIVE */
    private final String status;
}
