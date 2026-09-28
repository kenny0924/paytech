package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialDetailView;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialSummaryView;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.CredentialFilter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface ApiCredentialManagementMapper {
    /** 按身份读取 Client 凭证的非秘密详情，保留所属 Tenant 状态。 */
    ApiCredentialDetailView credential(@Param("id") long id);
    /** 在调用方本库事务中锁定目标行，供版本校验及条件更新使用。 */
    ApiCredentialManagement lock(@Param("id") long id);
    /** 分页查询 Client 凭证的非秘密信息，过滤与总数使用同一口径。 */
    java.util.List<ApiCredentialSummaryView> credentials(@Param("filter") CredentialFilter filter, @Param("asOf") Instant asOf, @Param("offset") long offset, @Param("size") int size);
    /** 使用与列表相同的筛选条件统计总数。 */
    long count(@Param("filter") CredentialFilter filter, @Param("asOf") Instant asOf);
    /** 撤销指定凭证并推进版本，保留其他凭证及授权明细。 */
    int revoke(@Param("id") long id, @Param("expectedVersion") int expectedVersion, @Param("now") Instant now);
}
