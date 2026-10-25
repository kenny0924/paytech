package com.paytech.commercial.domain.relationship;

import java.util.ArrayList;
import java.util.List;

import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundFlow;
import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.commercial.api.enums.RelationshipSubjectType;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 商业关系快照：商业关系角色到主体的唯一推导点（决定 9.2）
 * <li>只由候选事实推导，同样事实得到同样快照；资格结果与生效模型版本不在其中。</li>
 * <li>本轮只支持 OE 来源 PARTNER：MOR 合同为 C1（8 个角色），PSP 合同为 C2（另加 ACQUIRER_SUB_MERCHANT）。资金流向恒为
 * THROUGH_CUSTODIAN。</li>
 * <li>bindings 按 {@link RelationshipRole} 声明顺序排列。</li>
 */
@Value
@Accessors(fluent = true)
public class RelationshipSnapshot {
    /** 当前推导规则版本。 */
    public static final int SNAPSHOT_MODEL_VERSION = 1;

    /** 推导规则版本；本轮恒为 1。 */
    int snapshotModelVersion;

    /** 资金流向。 */
    FundFlow fundFlow;

    /** 合同业务模式。 */
    BusinessMode businessMode;

    /** 角色绑定，按角色声明顺序。 */
    List<RoleBinding> bindings;

    /**
     * 按 PARTNER 来源推导 C1 / C2 快照
     *
     * @param businessMode Client 合同业务模式
     * @param client Client 主体（clientId 与 Party）
     * @param entityNo 收单 OE 编号
     * @param entityPartyId 收单 OE 的 Party
     * @param provider OE 提供方（localPartnerId 与 Party）
     * @param platformPartyId Partner 合同唯一 PLATFORM_SIGNATORY 的 Party
     * @return 快照
     */
    public static RelationshipSnapshot derive(BusinessMode businessMode, Party client, String entityNo,
        Long entityPartyId, Party provider, Long platformPartyId) {
        boolean merchantOfRecord = businessMode == BusinessMode.MOR;
        RoleBinding clientBinding = binding(RelationshipRole.CLIENT, RelationshipSubjectType.CLIENT, client);
        RoleBinding entityBinding = new RoleBinding(RelationshipRole.ACQUIRING_OE, RelationshipSubjectType.OPERATING_ENTITY,
            entityNo, entityPartyId);
        RoleBinding providerBinding = binding(RelationshipRole.OE_PROVIDER, RelationshipSubjectType.LOCAL_PARTNER, provider);
        RoleBinding seller = merchantOfRecord ? as(entityBinding, RelationshipRole.SELLER)
            : as(clientBinding, RelationshipRole.SELLER);
        List<RoleBinding> bindings = new ArrayList<>();
        bindings.add(clientBinding);
        bindings.add(seller);
        bindings.add(entityBinding);
        bindings.add(providerBinding);
        bindings.add(as(entityBinding, RelationshipRole.FUNDS_CUSTODIAN));
        bindings.add(as(seller, RelationshipRole.TAX_RESPONSIBLE));
        bindings.add(as(providerBinding, RelationshipRole.PLATFORM_FEE_PAYER));
        if (!merchantOfRecord) {
            bindings.add(as(clientBinding, RelationshipRole.ACQUIRER_SUB_MERCHANT));
        }
        bindings.add(new RoleBinding(RelationshipRole.PLATFORM, RelationshipSubjectType.PLATFORM, null, platformPartyId));
        return new RelationshipSnapshot(SNAPSHOT_MODEL_VERSION, FundFlow.THROUGH_CUSTODIAN, businessMode,
            List.copyOf(bindings));
    }

    /** 十进制 ID 主体的绑定。 */
    private static RoleBinding binding(RelationshipRole role, RelationshipSubjectType type, Party party) {
        return new RoleBinding(role, type, Long.toString(party.subjectId()), party.partyId());
    }

    /** 同一主体换一个角色。 */
    private static RoleBinding as(RoleBinding source, RelationshipRole role) {
        return new RoleBinding(role, source.subjectType(), source.subjectId(), source.partyId());
    }

    /** 以十进制 ID 标识的主体及其法律主体。 */
    @Value
    @Accessors(fluent = true)
    public static class Party {
        /** 主体 ID（clientId 或 localPartnerId）。 */
        long subjectId;

        /** 法律主体 Party ID。 */
        Long partyId;
    }
}
