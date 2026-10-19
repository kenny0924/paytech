package com.paytech.commercial.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeHistory;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.BusinessChangeSnapshots;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 批准与驳回共用的锁内校验：版本 PENDING_APPROVAL，Maker 取自版本 Child 当前快照（必须是提交快照）的 create_by，Checker ≠ Maker
 * <li>PENDING_APPROVAL 只能由提交进入，且期间版本不再变化，因此 Child 当前快照即提交快照；否则为系统数据不一致。</li>
 */
@Component
@AllArgsConstructor
public class CommercialTermReviewSupport {
    /** 提交快照的原因代码。 */
    static final String SUBMITTED = "TERM_VERSION_SUBMITTED";

    private final CommercialTermLoader loader;
    private final BusinessChangeVersionRepository businessChangeVersionRepository;

    /**
     * 校验并构造审批 Context
     *
     * @param request 审批命令
     * @param version 已读取的版本
     * @return 含 Maker 的 Context
     */
    public CommercialTermVersionContext reviewContext(CommercialTermVersionCommandRequest request,
        CommercialTermVersion version) {
        if (version.getStatus() != CommercialTermVersionStatus.PENDING_APPROVAL) {
            throw new BizCheckFailException("只有 PENDING_APPROVAL 版本可以审批: " + version.getTermVersionId() + ", 状态 "
                + version.getStatus().getCode());
        }
        long maker = maker(loader.versionChild(version));
        if (request.getOperatorAccountId() == maker) {
            throw new BizCheckFailException("审批人不能是提交人: " + maker + ", 版本 " + version.getTermVersionId());
        }
        return loader.commandContext(request, version).makerAccountId(maker).build();
    }

    /** 版本 Child 当前快照必须是提交快照，返回其操作人。 */
    long maker(BusinessChangeHistory child) {
        BusinessChangeVersion submitted =
            businessChangeVersionRepository.findByHistoryAndVersion(child.getHistoryId(), child.getCurrentVersion());
        if (submitted == null || !SUBMITTED.equals(BusinessChangeSnapshots.reasonCode(submitted.getSnapshotJson()))) {
            throw new ErrorException("待审批版本缺少提交快照: " + child.getRecordId());
        }
        return submitted.getCreateBy();
    }
}
