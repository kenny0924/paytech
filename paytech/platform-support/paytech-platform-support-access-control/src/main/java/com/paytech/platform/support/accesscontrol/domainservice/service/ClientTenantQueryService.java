package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ClientTenant;

/** 按 CCT Client ID 解析 Platform CLIENT 租户；供其他模块使用，不返回持久化对象。 */
public interface ClientTenantQueryService {

    /**
     * 读取 Client 对应的 CLIENT 租户
     *
     * @param clientId CCT Client ID
     * @return 租户 ID；Client 未开通平台访问时为空
     */
    Long findClientTenantId(long clientId);

    /**
     * 按 Client ID 或名称分页查找 CLIENT 租户
     * <li>keyword 为 1—18 位数字时按 Client ID 精确匹配，否则按名称包含匹配；为空时返回全部</li>
     * <li>不过滤来源或安全状态，已暂停的 Client 仍可查看</li>
     *
     * @param keyword 去除首尾空白后最多 255 字符，可空
     * @param paging 已校验的分页
     * @return 按租户 ID 升序的租户概况与总数
     */
    PagingResponseBody<ClientTenant> searchClients(String keyword, PagingRequestBody paging);
}
