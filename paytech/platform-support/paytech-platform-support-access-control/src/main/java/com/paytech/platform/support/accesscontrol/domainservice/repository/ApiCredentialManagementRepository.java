package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialDetailView;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialSummaryView;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.CredentialFilter;

/** Client API 管理持久化，不向其他模块发布 DataObject。 */
public interface ApiCredentialManagementRepository {
    /** 按身份读取 Client 凭证的非秘密详情，保留所属 Tenant 状态。 */
    ApiCredentialDetailView credential(long id);
    /** 在调用方本库事务中锁定目标行，供版本校验及条件更新使用。 */
    ApiCredentialManagement lock(long id);
    /** 分页查询 Client 凭证的非秘密信息，过滤与总数使用同一口径。 */
    java.util.List<ApiCredentialSummaryView> credentials(CredentialFilter filter, Instant asOf, long offset, int size);
    /** 使用与列表相同的筛选条件统计总数。 */
    long count(CredentialFilter filter, Instant asOf);
    /** 按状态及版本条件撤销，影响行数必须为一，并同步原对象的状态、版本和时间。 */
    void revoke(ApiCredentialManagement credential, Instant now);
}
