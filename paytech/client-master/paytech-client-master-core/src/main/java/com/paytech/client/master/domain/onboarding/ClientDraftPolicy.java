package com.paytech.client.master.domain.onboarding;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.foundation.common.error.*;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
/** 当前申请与共享主体的冻结规则。 */
public final class ClientDraftPolicy {
    private ClientDraftPolicy() {}
    public static ClientDraftIdentity requireValid(ClientDraftIdentity row) {
        if (row==null) throw new FailException(APPLICATION_NOT_FOUND);
        if (row.profileId()==null || row.businessProfileId()==null || row.clientType()==null || row.status()==null)
            throw new ErrorException(DRAFT_CONTEXT_INVALID);
        boolean unfinished=switch(row.status()) { case DRAFT,SUBMITTED,UNDER_REVIEW,RETURNED -> true; default -> false; };
        if (unfinished?!Integer.valueOf(1).equals(row.activeSlot()):row.activeSlot()!=null) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        return row;
    }
    /**
     * 人员资料可编辑：申请可编辑、资料未批准、未被本库或合同共享且属于本申请草稿。
     * <li>合同引用来自 commercial（design/06 §2.2）；无法确认时 usage 失败关闭为已引用。</li>
     */
    public static boolean isPersonEditable(ClientDraftIdentity application,ClientPersonIdentity person,ClientAgreementPartyUsage usage) {
        return isEditable(application) && person.approvedTime()==null && !person.shared()
            && Long.valueOf(application.applicationId()).equals(person.draftApplicationId()) && !usage.shares(person);
    }
    public static boolean isEditable(ClientDraftIdentity row) {
        return (row.status()==ClientApplicationStatus.DRAFT || row.status()==ClientApplicationStatus.RETURNED)
            && Integer.valueOf(1).equals(row.activeSlot()) && row.approvedTime()==null
            && Long.valueOf(row.applicationId()).equals(row.draftApplicationId());
    }
}
