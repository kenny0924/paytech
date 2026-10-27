package com.paytech.operations.console.bff.service.model.clientreview.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审核工作台、Client 列表与入驻码页共用的 PayFac 选项
 * <li>一次读取 client-master 前 100 个 ACTIVE OE_PROVIDER Partner（按 localPartnerId 升序），页面据此显示名称，不按行请求。</li>
 */
@Getter
@AllArgsConstructor
public class ResponsiblePartnerListResponseBody {

    /** PayFac 选项 */
    private final List<ResponsiblePartnerResponseBody> items;
}
