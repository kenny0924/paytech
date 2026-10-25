package com.paytech.client.master.domainservice.service;

import com.paytech.client.master.domain.entityadmin.ClientSummaryView;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminDetail;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminView;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminDetail;
import com.paytech.client.master.domain.entityadmin.OperatingEntityAdminView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 运营主体、Local Partner 与 Client 主体的运营读取；只读本库，不开事务
 * <li>列表与详情允许观察相邻的已提交状态；修改以根修订号 CAS 防止覆盖。</li>
 */
public interface EntityAdminQueryService {
    /**
     * 经营主体分页
     *
     * @param status OE 状态 Code，null 不限
     * @param sourceType 当前来源类型 Code，null 不限
     * @param country 经营国家，null 不限
     * @param paging 已校验的分页参数
     * @return 按 entityNo 升序的分页结果
     */
    PagingResponseBody<OperatingEntityAdminView> operatingEntities(String status, String sourceType, String country,
        PagingRequestBody paging);

    /**
     * 经营主体详情
     *
     * @param entityNo OE 编号
     * @return 组合行、提供方角色、准入范围与卖方披露
     *         <li>不存在抛出 OPERATING_ENTITY_NOT_FOUND。</li>
     */
    OperatingEntityAdminDetail operatingEntity(String entityNo);

    /**
     * Local Partner 分页
     *
     * @param status 状态 Code，null 不限
     * @param role ACTIVE 角色 Code，null 不限
     * @param paging 已校验的分页参数
     * @return 按 localPartnerId 升序、已补齐角色的分页结果
     */
    PagingResponseBody<LocalPartnerAdminView> localPartners(String status, String role, PagingRequestBody paging);

    /**
     * Local Partner 详情
     *
     * @param localPartnerId Local Partner ID
     * @return 组合行（含角色）与提供的经营主体
     *         <li>不存在抛出 LOCAL_PARTNER_NOT_FOUND。</li>
     */
    LocalPartnerAdminDetail localPartner(long localPartnerId);

    /**
     * Client 主体摘要
     *
     * @param clientId Client ID
     * @return Client、Party 类型与法定名称
     *         <li>不存在抛出 CLIENT_NOT_FOUND。</li>
     */
    ClientSummaryView clientSummary(long clientId);
}
