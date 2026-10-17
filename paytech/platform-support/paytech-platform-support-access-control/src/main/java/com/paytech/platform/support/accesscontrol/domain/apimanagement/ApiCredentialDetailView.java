package com.paytech.platform.support.accesscontrol.domain.apimanagement;

import com.paytech.foundation.common.error.ErrorException;

import lombok.Getter;
import lombok.Setter;

/** 凭证与 Tenant 的详情投影，复用摘要字段及展示规则。 */
@Getter
@Setter
public class ApiCredentialDetailView extends ApiCredentialSummaryView {

    /** 同 Tenant 的轮换来源凭证标识；未轮换时为空。 */
    private Long rotatedFromId;

    /** 校验已读取的轮换来源，不在 View 内查询数据库。 */
    public void validateRotationSource(ApiCredentialDetailView origin) {
        if (rotatedFromId != null && (origin == null || rotatedFromId.equals(getCredentialId())
            || !getTenantId().equals(origin.getTenantId()))) {
            throw new ErrorException("凭证轮换来源不属于同一 Tenant");
        }
    }
}
