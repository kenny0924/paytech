package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.domain.apicredential.ApiCredentialCandidateView;

/** PLT-I03 API Credential 候选的本库只读 SQL 边界。 */
public interface ApiCredentialMapper {

    /** 定位候选并关联同一租户，缺失租户仍返回候选供上层失败关闭。 */
    List<ApiCredentialCandidateView> selectCandidates(@Param("keyPrefix") String keyPrefix,
        @Param("limit") int limit);
}
