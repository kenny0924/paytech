package com.paytech.commercial.domainservice.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.domainservice.repository.TermVersionAcceptanceRepository;
import com.paytech.commercial.core.dal.dataobject.TermVersionAcceptance;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.BusinessChangeSnapshot;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.BusinessChangeOperation;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/**
 * CommercialTermService 实现：只访问 mor_commercial，每个写方法一个 TransactionTemplate 事务
 * <li>BCR 统一经 BusinessChangeHistoryWriter：方案为 COMMERCIAL_TERM_PLAN Root（每条命令 CAS 推进并写方案快照），版本为
 * COMMERCIAL_TERM_VERSION 受审计 Child；同一命令的快照共享 triggerId、triggerType 与原因。</li>
 */
@Service
@AllArgsConstructor
public class CommercialTermServiceImpl implements CommercialTermService {
    private final TransactionTemplate transactionTemplate;
    private final BusinessChangeHistoryWriter historyWriter;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;
    private final TermVersionAcceptanceRepository termVersionAcceptanceRepository;

    /** 方案、BCR Root（版本 1）与 CREATE 快照同事务插入。 */
    @Override
    public CommercialTermVersionView createPlan(CommercialTermVersionContext context) {
        return transactionTemplate.execute(status -> {
            CommercialTermPlan plan = context.getPlan();
            commercialTermPlanRepository.insert(plan);
            BusinessChangeRecord root = historyWriter.createRoot(BusinessChangeRecordAggregateRootType.COMMERCIAL_TERM_PLAN,
                plan.getTermPlanId(), BusinessChangeReason.TERM_PLAN_CREATED,
                BusinessChangeSnapshot.commercialTermPlanData(plan), context.getOperatorAccountId(), context.getNow());
            return new CommercialTermVersionView(plan, null, root.getCurrentVersion());
        });
    }

    /** Root CAS 后插入版本与复制行，再为版本创建 Child 与 CREATE 快照。 */
    @Override
    public void createVersion(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, BusinessChangeReason.TERM_VERSION_CREATED);
            CommercialTermVersion version = context.getVersion();
            commercialTermVersionRepository.insert(version);
            insertPriceSet(context);
            historyWriter.createChild(context.getPlanRoot(), BusinessChangeRecordRecordType.COMMERCIAL_TERM_VERSION,
                version.getTermVersionId(), triggerId, BusinessChangeReason.TERM_VERSION_CREATED,
                withPriceCount(context, version), context.getOperatorAccountId(), context.getNow());
        });
    }

    /** Root CAS 后删除旧价格集合并插入新集合。 */
    @Override
    public void replacePrices(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, BusinessChangeReason.TERM_VERSION_PRICES_REPLACED);
            commercialTermComponentBandRepository.deleteByComponentIds(context.getReplacedComponentIds());
            commercialTermComponentRepository.deleteByPriceIds(context.getReplacedPriceIds());
            int deleted = commercialTermPriceRepository.deleteByVersionId(context.getVersion().getTermVersionId());
            if (deleted != context.getReplacedPriceIds().size()) {
                throw new ErrorException("价格集合删除行数不符: " + context.getVersion().getTermVersionId());
            }
            insertPriceSet(context);
            changeVersion(context, context.getVersion(), triggerId, BusinessChangeReason.TERM_VERSION_PRICES_REPLACED,
                withPriceCount(context, context.getVersion()));
        });
    }

    /** Root CAS 后追加模板价格集合，首次补齐写入来源模板版本。 */
    @Override
    public void fillFromTemplate(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, BusinessChangeReason.TERM_VERSION_FILLED_FROM_TEMPLATE);
            insertPriceSet(context);
            CommercialTermVersion version = context.getVersion();
            if (context.getFillSourceTemplateVersionId() != null) {
                commercialTermVersionRepository.updateSourceTemplate(version, context.getFillSourceTemplateVersionId(),
                    context.getNow());
            }
            Map<String, Object> data = BusinessChangeSnapshot.commercialTermVersionData(version);
            data.put("addedPriceCount", Integer.toString(context.getPrices().size()));
            changeVersion(context, version, triggerId, BusinessChangeReason.TERM_VERSION_FILLED_FROM_TEMPLATE, data);
        });
    }

    /** Root CAS 后插入签署记录，APPROVED → CLIENT_SIGNED。 */
    @Override
    public void recordAcceptance(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, BusinessChangeReason.TERM_VERSION_ACCEPTED);
            TermVersionAcceptance acceptance = context.getAcceptance();
            termVersionAcceptanceRepository.insert(acceptance);
            commercialTermVersionRepository.updateStatus(context.getVersion(), CommercialTermVersionStatus.CLIENT_SIGNED, null,
                context.getNow());
            Map<String, Object> data = BusinessChangeSnapshot.commercialTermVersionData(context.getVersion());
            data.put("versionAcceptanceId", Long.toString(acceptance.getVersionAcceptanceId()));
            data.put("partyId", Long.toString(acceptance.getPartyId()));
            data.put("acceptanceReference", acceptance.getAcceptanceReference());
            data.put("fileId", Long.toString(acceptance.getFileId()));
            data.put("contentHash", acceptance.getContentHash());
            changeVersion(context, context.getVersion(), triggerId, BusinessChangeReason.TERM_VERSION_ACCEPTED, data);
        });
    }

    /** DRAFT → PENDING_APPROVAL，记录 Maker。 */
    @Override
    public void submit(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, BusinessChangeReason.TERM_VERSION_SUBMITTED);
            commercialTermVersionRepository.updateStatus(context.getVersion(), CommercialTermVersionStatus.PENDING_APPROVAL,
                null, context.getNow());
            Map<String, Object> data = BusinessChangeSnapshot.commercialTermVersionData(context.getVersion());
            data.put("makerAccountId", Long.toString(context.getOperatorAccountId()));
            changeVersion(context, context.getVersion(), triggerId, BusinessChangeReason.TERM_VERSION_SUBMITTED, data);
        });
    }

    /** PENDING_APPROVAL → APPROVED，记录 Maker 与 Checker。 */
    @Override
    public void approve(CommercialTermVersionContext context) {
        review(context, CommercialTermVersionStatus.APPROVED, BusinessChangeReason.TERM_VERSION_APPROVED);
    }

    /** PENDING_APPROVAL → DRAFT，记录 Maker 与 Checker。 */
    @Override
    public void reject(CommercialTermVersionContext context) {
        review(context, CommercialTermVersionStatus.DRAFT, BusinessChangeReason.TERM_VERSION_REJECTED);
    }

    /** 关闭前一 ACTIVE 窗口，写入生效起点后 APPROVED → SCHEDULED。 */
    @Override
    public void schedule(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, BusinessChangeReason.TERM_VERSION_SCHEDULED);
            closePreviousWindow(context, triggerId);
            CommercialTermVersion version = context.getVersion();
            commercialTermVersionRepository.updateEffectivePeriod(version, context.getEffectiveFrom(), null, context.getNow());
            commercialTermVersionRepository.updateStatus(version, CommercialTermVersionStatus.SCHEDULED, null,
                context.getNow());
            changeVersion(context, version, triggerId, BusinessChangeReason.TERM_VERSION_SCHEDULED,
                BusinessChangeSnapshot.commercialTermVersionData(version));
        });
    }

    /**
     * 方案首次生效先转 ACTIVE（使 Root 快照为提交后状态），再 Root CAS；释放旧 ACTIVE 槽位后占用新槽位
     * <li>加入调用方事务，发布任务由激活 Processor 在同一事务登记。</li>
     */
    @Override
    public void activate(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            activatePlan(context);
            long triggerId = advanceRoot(context, BusinessChangeReason.TERM_VERSION_ACTIVATED);
            supersede(context, triggerId);
            CommercialTermVersion version = context.getVersion();
            commercialTermVersionRepository.updateStatus(version, CommercialTermVersionStatus.ACTIVE, 1, context.getNow());
            changeVersion(context, version, triggerId, BusinessChangeReason.TERM_VERSION_ACTIVATED,
                BusinessChangeSnapshot.commercialTermVersionData(version));
        });
    }

    /** 版本、方案与方案根当前版本。 */
    @Override
    public CommercialTermVersionView getVersion(long termVersionId) {
        CommercialTermVersion version = commercialTermVersionRepository.findById(termVersionId);
        if (version == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "商业条款版本不存在: " + termVersionId);
        }
        CommercialTermPlan plan = commercialTermPlanRepository.findById(version.getTermPlanId());
        Long changeVersion = businessChangeRecordRepository.findRootVersion(
            BusinessChangeRecordAggregateRootType.COMMERCIAL_TERM_PLAN, Long.toString(plan.getTermPlanId()));
        if (changeVersion == null) {
            throw new ErrorException("商业条款方案缺少 BCR 根: " + plan.getTermPlanId());
        }
        return new CommercialTermVersionView(plan, version, changeVersion);
    }

    /** 批准或驳回的共同保存。 */
    void review(CommercialTermVersionContext context, CommercialTermVersionStatus target, BusinessChangeReason reason) {
        transactionTemplate.executeWithoutResult(status -> {
            long triggerId = advanceRoot(context, reason);
            commercialTermVersionRepository.updateStatus(context.getVersion(), target, null, context.getNow());
            Map<String, Object> data = BusinessChangeSnapshot.commercialTermVersionData(context.getVersion());
            data.put("makerAccountId", Long.toString(context.getMakerAccountId()));
            data.put("checkerAccountId", Long.toString(context.getOperatorAccountId()));
            changeVersion(context, context.getVersion(), triggerId, reason, data);
        });
    }

    /** 排期时把前一 ACTIVE 的生效终点写成新起点（状态不变），使按时点选取在激活前也无重叠。 */
    void closePreviousWindow(CommercialTermVersionContext context, long triggerId) {
        CommercialTermVersion previous = context.getSupersededVersion();
        if (previous == null) {
            return;
        }
        commercialTermVersionRepository.updateEffectivePeriod(previous, previous.getEffectiveFrom(),
            context.getEffectiveFrom(), context.getNow());
        changeVersion(context, previous, triggerId, BusinessChangeReason.TERM_VERSION_SCHEDULED,
            BusinessChangeSnapshot.commercialTermVersionData(previous));
    }

    /** 前一 ACTIVE 先释放槽位成为 SUPERSEDED，再把生效终点写成新版本起点。 */
    void supersede(CommercialTermVersionContext context, long triggerId) {
        CommercialTermVersion superseded = context.getSupersededVersion();
        if (superseded == null) {
            return;
        }
        commercialTermVersionRepository.updateStatus(superseded, CommercialTermVersionStatus.SUPERSEDED, null,
            context.getNow());
        commercialTermVersionRepository.updateEffectivePeriod(superseded, superseded.getEffectiveFrom(),
            context.getVersion().getEffectiveFrom(), context.getNow());
        changeVersion(context, superseded, triggerId, BusinessChangeReason.TERM_VERSION_ACTIVATED,
            BusinessChangeSnapshot.commercialTermVersionData(superseded));
    }

    /** 方案首个版本生效时 DRAFT → ACTIVE；方案 Root 快照由同一命令的 Root CAS 写出。 */
    void activatePlan(CommercialTermVersionContext context) {
        CommercialTermPlan plan = context.getPlan();
        if (plan.getStatus() != CommercialTermPlanStatus.DRAFT) {
            return;
        }
        commercialTermPlanRepository.updateStatus(plan, CommercialTermPlanStatus.ACTIVE, context.getNow());
    }

    /** 方案 Root CAS 到期望版本 + 1 并写方案快照，返回本命令 triggerId。 */
    long advanceRoot(CommercialTermVersionContext context, BusinessChangeReason reason) {
        return historyWriter.advanceRoot(context.getPlanRoot(), context.getExpectedChangeVersion(), triggerType(context),
            reason, BusinessChangeSnapshot.commercialTermPlanData(context.getPlan()), context.getOperatorAccountId(),
            context.getNow());
    }

    /** 版本 Child 指针指向本次 Root 版本并追加 UPDATE 快照。 */
    void changeVersion(CommercialTermVersionContext context, CommercialTermVersion version, long triggerId,
        BusinessChangeReason reason, Map<String, Object> data) {
        historyWriter.changeChild(context.getPlanRoot(), BusinessChangeRecordRecordType.COMMERCIAL_TERM_VERSION,
            version.getTermVersionId(), BusinessChangeOperation.UPDATE, triggerType(context), triggerId, reason, data,
            context.getOperatorAccountId(), context.getNow());
    }

    /** 按价格 → 组件 → 档位插入新集合（空集合不访问数据库）。 */
    void insertPriceSet(CommercialTermVersionContext context) {
        commercialTermPriceRepository.insertAll(context.getPrices());
        commercialTermComponentRepository.insertAll(context.getComponents());
        commercialTermComponentBandRepository.insertAll(context.getBands());
    }

    /** 版本审计视图追加本次价格条数。 */
    static Map<String, Object> withPriceCount(CommercialTermVersionContext context, CommercialTermVersion version) {
        Map<String, Object> data = BusinessChangeSnapshot.commercialTermVersionData(version);
        data.put("priceCount", Integer.toString(context.getPrices().size()));
        return data;
    }

    /** 运营命令或系统命令。 */
    static String triggerType(CommercialTermVersionContext context) {
        return context.isSystemCommand() ? BusinessChangeSnapshot.TRIGGER_SYSTEM : BusinessChangeSnapshot.TRIGGER_COMMAND;
    }
}
