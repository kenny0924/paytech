package com.paytech.commercial.test.mocker.local.agreement;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.AgreementDocumentRefRepository;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementDocumentRefBuilder;
import com.paytech.commercial.test.builder.AgreementOperatingEntityBuilder;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

import lombok.AllArgsConstructor;

/**
 * 运营写入用例的合同前置数据：经正式 Repository 插入合同行、AGREEMENT BCR Root 与参与方 / 文件 / 覆盖 OE，不调用被测写入命令
 * <li>无可变状态；插入的行由 CommercialDataCleaner 在每例结束后统一清理。Root history_id 为合同 ID + 7，确定且不与其他夹具冲突。</li>
 */
@TestComponent
@AllArgsConstructor
public class AgreementWriteMocker {
    private final AgreementRepository agreementRepository;
    private final AgreementPartyRepository agreementPartyRepository;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final AgreementDocumentRefRepository agreementDocumentRefRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;

    /**
     * 插入合同与其 Root（当前版本 rootVersion），返回可继续追加子行的 Mocker
     *
     * @param builder 合同 Builder
     * @param rootVersion Root 当前版本
     * @return 持有该合同的 Mocker
     */
    public Mocker mock(AgreementBuilder builder, long rootVersion) {
        Agreement agreement = builder.build();
        agreementRepository.insert(agreement);
        BusinessChangeRecord root = new BusinessChangeRecord();
        root.setHistoryId(agreement.getAgreementId() + 7);
        root.setRecordType(BusinessChangeRecordRecordType.AGREEMENT);
        root.setRecordId(Long.toString(agreement.getAgreementId()));
        root.setAggregateRootType(BusinessChangeRecordAggregateRootType.AGREEMENT);
        root.setAggregateRootId(Long.toString(agreement.getAgreementId()));
        root.setParentHistoryId(null);
        root.setCurrentVersion(rootVersion);
        root.setCreateTime(CheckoutFixture.NOW);
        root.setUpdateTime(CheckoutFixture.NOW);
        businessChangeRecordRepository.insert(root);
        return new Mocker(agreement, root);
    }

    /** 已插入合同的子行追加器。 */
    public class Mocker {
        private final Agreement agreement;
        private final BusinessChangeRecord root;
        private final List<AgreementParty> parties = new ArrayList<>();

        private Mocker(Agreement agreement, BusinessChangeRecord root) {
            this.agreement = agreement;
            this.root = root;
        }

        /**
         * 追加一条参与方角色，所属合同取本合同
         *
         * @param builder 参与方 Builder
         * @return 本 Mocker
         */
        public Mocker party(AgreementPartyBuilder builder) {
            AgreementParty party = builder.agreementId(agreement.getAgreementId()).build();
            agreementPartyRepository.insertAll(List.of(party));
            parties.add(party);
            return this;
        }

        /**
         * 追加一份合同文件，所属合同取本合同
         *
         * @param builder 文件 Builder
         * @return 本 Mocker
         */
        public Mocker document(AgreementDocumentRefBuilder builder) {
            AgreementDocumentRef document = builder.agreementId(agreement.getAgreementId()).build();
            agreementDocumentRefRepository.insertAll(List.of(document));
            return this;
        }

        /**
         * 追加一个覆盖 OE（PARTNER 合同）
         *
         * @param id 覆盖关系 ID
         * @param entityNo OE
         * @return 本 Mocker
         */
        public Mocker operatingEntity(long id, String entityNo) {
            AgreementOperatingEntity row = AgreementOperatingEntityBuilder.template().agreementOperatingEntityId(id)
                .agreementId(agreement.getAgreementId()).entityNo(entityNo).build();
            agreementOperatingEntityRepository.insertAll(List.of(row));
            return this;
        }

        /**
         * 为已插入的订阅或开通项登记受审计 Child 的历史身份（父级为本合同 Root，指针为 Root 当前版本，无快照）
         *
         * @param childType AGREEMENT_PRODUCT 或 AGREEMENT_CAPABILITY
         * @param childId 订阅行或开通项 ID
         * @return 本 Mocker
         */
        public Mocker auditedChild(BusinessChangeRecordRecordType childType, long childId) {
            BusinessChangeRecord child = new BusinessChangeRecord();
            child.setHistoryId(childId + 7);
            child.setRecordType(childType);
            child.setRecordId(Long.toString(childId));
            child.setAggregateRootType(BusinessChangeRecordAggregateRootType.AGREEMENT);
            child.setAggregateRootId(Long.toString(agreement.getAgreementId()));
            child.setParentHistoryId(root.getHistoryId());
            child.setCurrentVersion(root.getCurrentVersion());
            child.setCreateTime(CheckoutFixture.NOW);
            child.setUpdateTime(CheckoutFixture.NOW);
            businessChangeRecordRepository.insert(child);
            return this;
        }

        /** 已插入的合同。 */
        public Agreement agreement() {
            return agreement;
        }

        /** 已插入的 Root。 */
        public BusinessChangeRecord root() {
            return root;
        }

        /** 已插入的参与方，按追加顺序。 */
        public List<AgreementParty> parties() {
            return List.copyOf(parties);
        }
    }
}
