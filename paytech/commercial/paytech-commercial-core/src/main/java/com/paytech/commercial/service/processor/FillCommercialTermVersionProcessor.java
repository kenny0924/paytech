package com.paytech.commercial.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.commercial.service.processor.request.FillCommercialTermVersionRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 从标准价目模板补齐 DRAFT 合同版本（POST /commercial-terms/v1/versions/{id}/fill-from-template）
 * <li>只用于 Client 合同方案；模板版本须为同条款类型的已生效模板版本。按合同已开通项选出模板价格，只追加当前版本缺失的（OE × 币种 × 画像），
 * 规则同基于模板生成；同 OE + 币种内优先级冲突时顺延。</li>
 * <li>方案锁持续等待；根 CAS、追加行、首次补齐时写入来源模板版本与版本 Child UPDATE 快照同事务写入。没有可补齐的行时不写入。</li>
 */
@Component
@AllArgsConstructor
public class FillCommercialTermVersionProcessor
    extends AbstractLockProcessor<FillCommercialTermVersionRequest, CommercialTermVersionView> {
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(FillCommercialTermVersionRequest request) {
        return LockCommand.lock(CommercialLocks.termPlan(loader.planIdOf(request.getVersionId())));
    }

    @Override
    protected CommercialTermVersionView processWithinLock(FillCommercialTermVersionRequest request) {
        // 版本须为 Client 合同方案的 DRAFT
        CommercialTermVersion version = loader.version(request.getVersionId());
        CommercialTermVersionContext base = loader.commandContext(CommercialTermVersionCommandRequest.operator(
            request.getVersionId(), request.getExpectedChangeVersion(), request.getOperatorAccountId()), version).build();
        checkFillable(base);
        CommercialTermVersion template = loader.templateVersion(request.getTemplateVersionId(), base.getPlan().getTermType());

        // 选出缺失的模板价格
        List<CommercialTermPrice> existing = loader.prices(version.getTermVersionId());
        TemplatePriceSelection selection = loader.templateSelection(base.getPlan(), template, version, existing, base.getNow());
        if (selection.getPrices().isEmpty()) {
            return base.toVersionView();
        }

        // 保存
        CommercialTermVersionContext context = loader.commandContext(CommercialTermVersionCommandRequest.operator(
                request.getVersionId(), request.getExpectedChangeVersion(), request.getOperatorAccountId()), version)
            .prices(selection.getPrices()).components(selection.getComponents()).bands(selection.getBands())
            .fillSourceTemplateVersionId(version.getSourceTemplateVersionId() == null ? template.getTermVersionId() : null)
            .build();
        commercialTermService.fillFromTemplate(context);
        return context.toVersionView();
    }

    /** 只有 Client 合同方案的 DRAFT 版本可以补齐。 */
    static void checkFillable(CommercialTermVersionContext context) {
        if (context.getPlan().getOwnerType() != CommercialTermOwnerType.CLIENT) {
            throw new BizCheckFailException("只有 Client 合同方案可以从标准价目模板补齐: 方案 " + context.getPlan().getTermPlanId());
        }
        if (context.getVersion().getStatus() != CommercialTermVersionStatus.DRAFT) {
            throw new BizCheckFailException("只有 DRAFT 版本可以从标准价目模板补齐: " + context.getVersion().getTermVersionId()
                + ", 状态 " + context.getVersion().getStatus().getCode());
        }
    }
}
