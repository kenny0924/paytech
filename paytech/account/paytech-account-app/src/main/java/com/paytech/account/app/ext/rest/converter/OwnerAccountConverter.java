package com.paytech.account.app.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.response.OwnerAccountEntryResponseBody;
import com.paytech.account.api.response.OwnerAccountResponseBody;
import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.domain.view.AccountEntryView;
import com.paytech.account.service.processor.request.ListAccountEntriesRequest;
import com.paytech.account.service.processor.request.ListOwnerAccountsRequest;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** Owner 账户与流水查询的 Wire 转换；分页走公共 PagingRequestBody，ID 与版本输出为十进制字符串，金额为 Money。 */
@Component
public class OwnerAccountConverter {

    /** 账户列表参数。 */
    public ListOwnerAccountsRequest toListRequest(String entityType, String entityId, String currency, String ownerType,
        String ownerId, String accountCode, String balanceBucket, String accountNo, Integer page, Integer size) {
        return new ListOwnerAccountsRequest(CodeEnumConverter.parse(AccountEntityType.class, entityType), entityId,
            currency, CodeEnumConverter.parseOptional(OwnerType.class, ownerType), ownerId, accountCode,
            CodeEnumConverter.parseOptional(BalanceBucket.class, balanceBucket), accountNo,
            PagingRequestBody.of(page, size));
    }

    /** 流水列表参数。 */
    public ListAccountEntriesRequest toEntriesRequest(String accountId, Integer page, Integer size) {
        return new ListAccountEntriesRequest(Long.parseLong(accountId), PagingRequestBody.of(page, size));
    }

    /** 一个账户及其余额。 */
    public OwnerAccountResponseBody toResponseBody(OwnerAccount account) {
        OwnerAccountResponseBody body = new OwnerAccountResponseBody();
        body.setAccountId(account.getId().toString());
        body.setAccountNo(account.getAccountNo());
        body.setAccountCode(account.getAccountCode());
        body.setOwnerType(account.getOwnerType().getCode());
        body.setOwnerId(account.getOwnerId());
        body.setBalanceBucket(account.getBalanceBucket().getCode());
        body.setNormalSide(account.getNormalSide().getCode());
        body.setAccountStatus(account.getAccountStatus().getCode());
        body.setDebitTotal(Money.of(account.getCurrency(), account.getDebitTotal()));
        body.setCreditTotal(Money.of(account.getCurrency(), account.getCreditTotal()));
        body.setNetBalance(Money.of(account.getCurrency(), account.getNetBalance()));
        body.setBalanceVersion(account.getBalanceVersion().toString());
        return body;
    }

    /** 一条流水及其批次来源。 */
    public OwnerAccountEntryResponseBody toResponseBody(AccountEntryView view) {
        AccountEntry entry = view.getEntry();
        AccountPosting posting = view.getPosting();
        OwnerAccountEntryResponseBody body = new OwnerAccountEntryResponseBody();
        body.setEntryId(entry.getId().toString());
        body.setPostingId(posting.getId().toString());
        body.setPostingSequence(posting.getPostingSequence().toString());
        body.setSourceType(posting.getSourceType().getCode());
        body.setFactId(CodeEnumConverter.text(posting.getFactId()));
        body.setFactVersion(CodeEnumConverter.text(posting.getFactVersion()));
        body.setFactType(view.getAppliedFact() == null ? null : view.getAppliedFact().getFactType());
        body.setMovementSourceType(view.getMovement() == null ? null : view.getMovement().getSourceType().getCode());
        body.setLineNo(entry.getLineNo().toString());
        body.setFactLineNo(CodeEnumConverter.text(entry.getFactLineNo()));
        body.setEntrySide(entry.getEntrySide().getCode());
        body.setAmount(Money.of(posting.getCurrency(), entry.getAmount()));
        body.setReversesEntryId(CodeEnumConverter.text(entry.getReversesEntryId()));
        body.setPostedTime(posting.getPostedTime());
        return body;
    }
}
