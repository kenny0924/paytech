package com.paytech.commercial.domain;

import java.util.List;

import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 合同列表查询条件：全部筛选可空，空集合状态表示不按状态筛选。 */
@Getter
@AllArgsConstructor
public class AgreementListQuery {
    /** Client ID，可空 */
    private final Long clientId;

    /** Local Partner ID，可空 */
    private final Long localPartnerId;

    /** 合同类型，可空 */
    private final AgreementAgreementType agreementType;

    /** 合同状态集合，空集合不筛选 */
    @NotNull
    private final List<AgreementStatus> statuses;

    /**
     * OE 编号，可空
     * <li>匹配 CLIENT 合同的 entity_no 与 PARTNER 合同的覆盖 OE。</li>
     */
    private final String entityNo;

    /**
     * 合同编号字面前缀，可空
     * <li>Repository 转义 LIKE 通配符后按前缀匹配。</li>
     */
    private final String agreementNoPrefix;

    /** 已校验的分页参数 */
    @NotNull
    private final PagingRequestBody paging;
}
