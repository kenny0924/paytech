package com.paytech.commercial.domainservice.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeHistory;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.BusinessChangeSnapshots;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.BusinessChangeOperation;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeSnapshotFormat;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/** CommercialTermService 实现：只访问 mor_commercial，每个写方法一个 TransactionTemplate 事务。 */
@Service
@AllArgsConstructor
public class CommercialTermServiceImpl implements CommercialTermService {
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final CommercialTermPriceRepository commercialTermPriceRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final BusinessChangeVersionRepository businessChangeVersionRepository;
    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final CommercialTermComponentRepository commercialTermComponentRepository;
    private final CommercialTermComponentBandRepository commercialTermComponentBandRepository;

    /** 方案、BCR 根与 CREATE 快照同事务插入。 */
    @Override
    public void createPlan(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            commercialTermPlanRepository.insert(context.getPlan());
            businessChangeRecordRepository.insert(context.getPlanHistory());
            appendSnapshot(context, context.getPlanHistory(), BusinessChangeOperation.CREATE, "TERM_PLAN_CREATED",
                planData(context.getPlan()));
        });
    }

    /** 根 CAS 后插入版本、Child、快照与复制行。 */
    @Override
    public void createVersion(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            advanceRoot(context);
            commercialTermVersionRepository.insert(context.getVersion());
            businessChangeRecordRepository.insert(context.getVersionHistory());
            Map<String, String> data = versionData(context.getVersion());
            data.put("priceCount", Integer.toString(context.getPrices().size()));
            appendSnapshot(context, context.getVersionHistory(), BusinessChangeOperation.CREATE, "TERM_VERSION_CREATED", data);
            insertPriceSet(context);
        });
    }

    /** 根 CAS 后删除旧价格集合并插入新集合。 */
    @Override
    public void replacePrices(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            advanceRoot(context);
            commercialTermComponentBandRepository.deleteByComponentIds(context.getReplacedComponentIds());
            commercialTermComponentRepository.deleteByPriceIds(context.getReplacedPriceIds());
            int deleted = commercialTermPriceRepository.deleteByVersionId(context.getVersion().getTermVersionId());
            if (deleted != context.getReplacedPriceIds().size()) {
                throw new ErrorException("价格集合删除行数不符: " + context.getVersion().getTermVersionId());
            }
            insertPriceSet(context);
            Map<String, String> data = versionData(context.getVersion());
            data.put("priceCount", Integer.toString(context.getPrices().size()));
            updateChild(context, context.getVersionHistory(), "TERM_VERSION_PRICES_REPLACED", data);
        });
    }

    /** DRAFT → PENDING_APPROVAL，记录 Maker。 */
    @Override
    public void submit(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            advanceRoot(context);
            commercialTermVersionRepository.updateStatus(context.getVersion(), CommercialTermVersionStatus.PENDING_APPROVAL,
                null, context.getNow());
            Map<String, String> data = versionData(context.getVersion());
            data.put("makerAccountId", Long.toString(context.getOperatorAccountId()));
            updateChild(context, context.getVersionHistory(), "TERM_VERSION_SUBMITTED", data);
        });
    }

    /** PENDING_APPROVAL → APPROVED，记录 Maker 与 Checker。 */
    @Override
    public void approve(CommercialTermVersionContext context) {
        review(context, CommercialTermVersionStatus.APPROVED, "TERM_VERSION_APPROVED");
    }

    /** PENDING_APPROVAL → DRAFT，记录 Maker 与 Checker。 */
    @Override
    public void reject(CommercialTermVersionContext context) {
        review(context, CommercialTermVersionStatus.DRAFT, "TERM_VERSION_REJECTED");
    }

    /** 关闭前一 ACTIVE 窗口，写入生效起点后 APPROVED → SCHEDULED。 */
    @Override
    public void schedule(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            advanceRoot(context);
            closePreviousWindow(context);
            CommercialTermVersion version = context.getVersion();
            commercialTermVersionRepository.updateEffectivePeriod(version, context.getEffectiveFrom(), null, context.getNow());
            commercialTermVersionRepository.updateStatus(version, CommercialTermVersionStatus.SCHEDULED, null,
                context.getNow());
            updateChild(context, context.getVersionHistory(), "TERM_VERSION_SCHEDULED", versionData(version));
        });
    }

    /** 释放旧 ACTIVE 槽位后占用新槽位，方案首次生效转 ACTIVE；加入调用方事务，发布任务由激活 Processor 在同一事务登记。 */
    @Override
    public void activate(CommercialTermVersionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            advanceRoot(context);
            supersede(context);
            CommercialTermVersion version = context.getVersion();
            commercialTermVersionRepository.updateStatus(version, CommercialTermVersionStatus.ACTIVE, 1, context.getNow());
            updateChild(context, context.getVersionHistory(), "TERM_VERSION_ACTIVATED", versionData(version));
            activatePlan(context);
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
    void review(CommercialTermVersionContext context, CommercialTermVersionStatus target, String reasonCode) {
        transactionTemplate.executeWithoutResult(status -> {
            advanceRoot(context);
            commercialTermVersionRepository.updateStatus(context.getVersion(), target, null, context.getNow());
            Map<String, String> data = versionData(context.getVersion());
            data.put("makerAccountId", Long.toString(context.getMakerAccountId()));
            data.put("checkerAccountId", Long.toString(context.getOperatorAccountId()));
            updateChild(context, context.getVersionHistory(), reasonCode, data);
        });
    }

    /** 排期时把前一 ACTIVE 的生效终点写成新起点（状态不变），使按时点选取在激活前也无重叠。 */
    void closePreviousWindow(CommercialTermVersionContext context) {
        CommercialTermVersion previous = context.getSupersededVersion();
        if (previous == null) {
            return;
        }
        commercialTermVersionRepository.updateEffectivePeriod(previous, previous.getEffectiveFrom(),
            context.getEffectiveFrom(), context.getNow());
        updateChild(context, context.getSupersededHistory(), "TERM_VERSION_WINDOW_CLOSED", versionData(previous));
    }

    /** 前一 ACTIVE 先释放槽位成为 SUPERSEDED，再把生效终点写成新版本起点。 */
    void supersede(CommercialTermVersionContext context) {
        CommercialTermVersion superseded = context.getSupersededVersion();
        if (superseded == null) {
            return;
        }
        commercialTermVersionRepository.updateStatus(superseded, CommercialTermVersionStatus.SUPERSEDED, null,
            context.getNow());
        commercialTermVersionRepository.updateEffectivePeriod(superseded, superseded.getEffectiveFrom(),
            context.getVersion().getEffectiveFrom(), context.getNow());
        updateChild(context, context.getSupersededHistory(), "TERM_VERSION_SUPERSEDED", versionData(superseded));
    }

    /** 方案首个版本生效时 DRAFT → ACTIVE，并为方案根写 UPDATE 快照。 */
    void activatePlan(CommercialTermVersionContext context) {
        CommercialTermPlan plan = context.getPlan();
        if (plan.getStatus() != CommercialTermPlanStatus.DRAFT) {
            return;
        }
        commercialTermPlanRepository.updateStatus(plan, CommercialTermPlanStatus.ACTIVE, context.getNow());
        appendSnapshot(context, context.getPlanHistory(), BusinessChangeOperation.UPDATE, "TERM_PLAN_ACTIVATED",
            planData(plan));
    }

    /** 方案根 CAS 到期望版本 + 1。 */
    void advanceRoot(CommercialTermVersionContext context) {
        businessChangeRecordRepository.advanceRoot(context.getPlanHistory(), context.getExpectedChangeVersion(),
            context.getNow());
    }

    /** Child 指针指向本次根版本并追加 UPDATE 快照。 */
    void updateChild(CommercialTermVersionContext context, BusinessChangeHistory child, String reasonCode,
        Map<String, String> data) {
        businessChangeRecordRepository.pointChild(child, context.nextChangeVersion(), context.getNow());
        appendSnapshot(context, child, BusinessChangeOperation.UPDATE, reasonCode, data);
    }

    /** 按审计身份当前版本追加一条快照。 */
    void appendSnapshot(CommercialTermVersionContext context, BusinessChangeHistory history, BusinessChangeOperation operation,
        String reasonCode, Map<String, String> data) {
        BusinessChangeVersion snapshot = new BusinessChangeVersion();
        snapshot.setChangeVersionId(idGenerator.nextId());
        snapshot.setHistoryId(history.getHistoryId());
        snapshot.setVersionNo(history.getCurrentVersion());
        snapshot.setOperationType(operation);
        snapshot.setSnapshotFormat(BusinessChangeSnapshotFormat.ENTITY_NO);
        snapshot.setCreateBy(context.getOperatorAccountId());
        snapshot.setCreateTime(context.getNow());
        snapshot.setSnapshotJson(BusinessChangeSnapshots.snapshot(history, snapshot, triggerType(context),
            context.getTriggerId(), reasonCode, data));
        businessChangeVersionRepository.insert(snapshot);
    }

    /** 按价格 → 组件 → 档位插入新集合（空集合不访问数据库）。 */
    void insertPriceSet(CommercialTermVersionContext context) {
        commercialTermPriceRepository.insertAll(context.getPrices());
        commercialTermComponentRepository.insertAll(context.getComponents());
        commercialTermComponentBandRepository.insertAll(context.getBands());
    }

    /** 运营命令或系统命令。 */
    static String triggerType(CommercialTermVersionContext context) {
        return context.isSystemCommand() ? BusinessChangeSnapshots.TRIGGER_SYSTEM : BusinessChangeSnapshots.TRIGGER_COMMAND;
    }

    /** 方案审计白名单字段。 */
    static Map<String, String> planData(CommercialTermPlan plan) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("planId", text(plan.getTermPlanId()));
        data.put("planCode", plan.getPlanCode());
        data.put("termType", plan.getTermType());
        data.put("agreementId", text(plan.getAgreementId()));
        data.put("entityNo", plan.getEntityNo());
        data.put("clientId", text(plan.getClientId()));
        data.put("localPartnerId", text(plan.getLocalPartnerId()));
        data.put("status", CodeEnum.defaultCode(plan.getStatus()));
        return data;
    }

    /** 版本审计白名单字段。 */
    static Map<String, String> versionData(CommercialTermVersion version) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("versionId", text(version.getTermVersionId()));
        data.put("planId", text(version.getTermPlanId()));
        data.put("agreementId", text(version.getAgreementId()));
        data.put("versionNo", text(version.getVersionNo()));
        data.put("status", CodeEnum.defaultCode(version.getStatus()));
        data.put("effectiveFrom", text(version.getEffectiveFrom()));
        data.put("effectiveTo", text(version.getEffectiveTo()));
        data.put("roundingMode", CodeEnum.defaultCode(version.getRoundingMode()));
        data.put("pricingModel", CodeEnum.defaultCode(version.getPricingModel()));
        data.put("feeTaxTreatment", CodeEnum.defaultCode(version.getFeeTaxTreatment()));
        return data;
    }

    /** 文本，空为 null。 */
    static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
