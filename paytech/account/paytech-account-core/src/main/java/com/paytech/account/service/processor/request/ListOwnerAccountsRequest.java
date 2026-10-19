package com.paytech.account.service.processor.request;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 分页查询主体与币种下的 Owner 账户；可选筛选为空表示不过滤。 */
@Getter
@AllArgsConstructor
public class ListOwnerAccountsRequest {
    /** 主体类型。 */
    @NotNull
    private final AccountEntityType entityType;

    /** 主体标识。 */
    @NotBlank
    private final String entityId;

    /** 大写三字母币种。 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}")
    private final String currency;

    /** 可选核算对象类型。 */
    private final OwnerType ownerType;

    /** 可选核算对象标识。 */
    private final String ownerId;

    /** 可选科目代码。 */
    private final String accountCode;

    /** 可选余额桶。 */
    private final BalanceBucket balanceBucket;

    /** 已校验的分页参数。 */
    @NotNull
    private final PagingRequestBody paging;
}
