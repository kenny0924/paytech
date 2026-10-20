package com.paytech.account.domainservice.service;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.domain.view.AccountEntryView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** Owner 账户与明细流水的分页读取；只读，不加锁、不开事务。 */
public interface OwnerAccountQueryService {

    /**
     * 分页读取主体与币种下的 Owner 账户；可选筛选为空表示不过滤，账户号为精确匹配
     *
     * @return 按科目代码、核算对象类型、核算对象标识、余额桶排序的账户分页
     */
    PagingResponseBody<OwnerAccount> pageAccounts(AccountEntityType entityType, String entityId, String currency,
        OwnerType ownerType, String ownerId, String accountCode, BalanceBucket bucket, String accountNo,
        PagingRequestBody paging);

    /**
     * 按主键读取账户
     *
     * @return 账户；不存在为空
     */
    OwnerAccount findAccount(long accountId);

    /**
     * 分页读取一个账户的流水及其批次与移动来源
     *
     * @return 按批次序号、行号升序的流水分页
     */
    PagingResponseBody<AccountEntryView> pageEntries(long accountId, PagingRequestBody paging);
}
