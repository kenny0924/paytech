package com.paytech.commercial.domainservice.service.impl;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.BusinessChangeSnapshot;
import com.paytech.commercial.domain.PricingProfileContext;
import com.paytech.commercial.domain.PricingProfileView;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.domainservice.service.PricingProfileService;

import lombok.AllArgsConstructor;

/**
 * PricingProfileService 实现：画像 BCR Root 类型沿用 PAYMENT_METHOD_PROFILE（物理表改名不改审计类型，database.md §8）
 * <li>写入使用注入的写事务模板，列表读取使用只读 REPEATABLE READ 模板。</li>
 */
@AllArgsConstructor
public class PricingProfileServiceImpl implements PricingProfileService {
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;
    private final PricingProfileRepository pricingProfileRepository;
    private final BusinessChangeHistoryWriter historyWriter;
    private final PricingProfileCondRepository pricingProfileCondRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;

    /** 画像、条件行与 Root 同一事务写入。 */
    @Override
    public void createProfile(PricingProfileContext context) {
        writeTransaction.executeWithoutResult(status -> {
            PricingProfile profile = context.getProfile();
            pricingProfileRepository.insert(profile);
            pricingProfileCondRepository.insertAll(context.getConditions());
            context.setRoot(historyWriter.createRoot(BusinessChangeRecordAggregateRootType.PAYMENT_METHOD_PROFILE,
                profile.getPricingProfileId(), BusinessChangeReason.PRICING_PROFILE_CREATED,
                BusinessChangeSnapshot.pricingProfileData(profile, context.getConditions()), context.getActorId(),
                context.getNow()));
        });
    }

    /** 以 DRAFT 与 update_time 为条件改写内容，整体替换条件行后写最终快照。 */
    @Override
    public void updateDraft(PricingProfileContext context) {
        writeTransaction.executeWithoutResult(status -> {
            PricingProfile profile = context.getProfile();
            pricingProfileRepository.updateDraft(profile, context.getNextProfileName(), profile.getProfileKind(),
                context.getNextPaymentMethodCode(), context.getNow());
            pricingProfileCondRepository.deleteByProfileId(profile.getPricingProfileId());
            pricingProfileCondRepository.insertAll(context.getConditions());
            advanceRoot(context, BusinessChangeReason.PRICING_PROFILE_UPDATED);
        });
    }

    /** 以当前状态为条件推进，写最终快照。 */
    @Override
    public void changeStatus(PricingProfileContext context, BusinessChangeReason reason) {
        writeTransaction.executeWithoutResult(status -> {
            pricingProfileRepository.updateStatus(context.getProfile(), context.getNextStatus(), context.getNow());
            advanceRoot(context, reason);
        });
    }

    /** 同一只读快照读取画像、条件与 Root 版本。 */
    @Override
    public List<PricingProfileView> listProfiles(PricingProfileKind kind, PricingProfileStatus status) {
        return readTransaction.execute(transaction -> {
            List<PricingProfile> profiles = pricingProfileRepository.findByKindAndStatus(kind, status);
            List<Long> ids = profiles.stream().map(PricingProfile::getPricingProfileId).toList();
            Map<Long, List<PricingProfileCond>> conditions = pricingProfileCondRepository.findByProfileIds(ids).stream()
                .collect(Collectors.groupingBy(PricingProfileCond::getPricingProfileId));
            Map<String, BusinessChangeRecord> roots = businessChangeRecordRepository
                .findRoots(BusinessChangeRecordAggregateRootType.PAYMENT_METHOD_PROFILE,
                    ids.stream().map(String::valueOf).toList())
                .stream().collect(Collectors.toMap(BusinessChangeRecord::getRecordId, Function.identity()));
            return profiles.stream().map(profile -> new PricingProfileView(profile,
                conditions.getOrDefault(profile.getPricingProfileId(), List.of()),
                rootVersion(roots, profile.getPricingProfileId()))).toList();
        });
    }

    /** Root CAS 并写画像最终快照（含条件行）。 */
    void advanceRoot(PricingProfileContext context, BusinessChangeReason reason) {
        historyWriter.advanceRoot(context.getRoot(), context.getExpectedChangeVersion(), reason,
            BusinessChangeSnapshot.pricingProfileData(context.getProfile(), context.getConditions()),
            context.getActorId(), context.getNow());
    }

    /** Root 当前版本；拆分前迁入且未接入 BCR 的历史画像为空（只读，写入时失败关闭）。 */
    static Long rootVersion(Map<String, BusinessChangeRecord> roots, long profileId) {
        BusinessChangeRecord root = roots.get(Long.toString(profileId));
        return root == null ? null : root.getCurrentVersion();
    }
}
