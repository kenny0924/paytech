package com.paytech.commercial.auto.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domainservice.repository.AgreementDocumentRefRepository;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementDocumentRefBuilder;
import com.paytech.commercial.test.builder.AgreementOperatingEntityBuilder;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.foundation.common.error.ConcurrentRequestException;

/**
 * 合同四表的完整行写入映射：插入、按主键 / 唯一键 / 父读取、生命周期与签署的条件更新、覆盖 OE 删除；真实 MyBatis 与本地 mor_commercial。
 * <li>本期没有生产写入方（B 线合同写入 Processor 尚未实现）；Checkout 只读投影由 CheckoutFactsMappingTest 覆盖。</li>
 *
 * @see AgreementRepository#insert(Agreement)
 * @see AgreementRepository#findByAgreementNo(String)
 * @see AgreementRepository#updateStatus(Agreement, AgreementStatus, Instant)
 * @see AgreementRepository#updateLifecycle(Agreement, AgreementStatus, Instant, Instant, Instant, Instant)
 * @see AgreementPartyRepository#insertAll(List)
 * @see AgreementPartyRepository#recordAcceptance(AgreementParty, AgreementParty, Instant)
 * @see AgreementPartyRepository#findByAcceptanceReference(String)
 * @see AgreementDocumentRefRepository#insertAll(List)
 * @see AgreementOperatingEntityRepository#findByAgreementIds(java.util.Collection)
 * @see AgreementOperatingEntityRepository#delete(AgreementOperatingEntity)
 */
class AgreementWriteRepositoryTest extends CommercialMockTestBase {
    /** 更新时点。 */
    private static final Instant LATER = Instant.parse("2026-09-12T08:30:00.123456Z");

    /** 合成合同。 */
    private static final long AGREEMENT_ID = AgreementBuilder.AGREEMENT_ID;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private AgreementPartyRepository partyRepository;

    @Autowired
    private AgreementDocumentRefRepository documentRepository;

    @Autowired
    private AgreementOperatingEntityRepository entityRepository;

    /** 合同插入后完整读回；编号唯一；状态与生命周期以当前状态为条件推进，陈旧对象失败且不改库。 */
    @Test
    void test_agreementInsertReadAndLifecycle() {
        Agreement agreement = AgreementBuilder.template().build();
        agreementRepository.insert(agreement);
        Agreement stale = agreementRepository.findById(AGREEMENT_ID);

        agreementRepository.updateStatus(agreement, AgreementStatus.PENDING_SIGNATURE, LATER);
        agreementRepository.updateLifecycle(agreement, AgreementStatus.SIGNED, null, LATER, null, LATER.plusSeconds(1));

        Agreement read = agreementRepository.findByAgreementNo(AgreementBuilder.AGREEMENT_NO);
        assertAll(() -> assertEquals(AGREEMENT_ID, read.getAgreementId()),
            () -> assertEquals(BusinessMode.MOR, read.getBusinessMode()),
            () -> assertEquals(AgreementBuilder.ENTITY_NO, read.getEntityNo()),
            () -> assertEquals(AgreementBuilder.ACTOR_ID, read.getCreateBy()),
            () -> assertEquals(AgreementStatus.SIGNED, read.getStatus()), () -> assertEquals(LATER, read.getSignedTime()),
            () -> assertNull(read.getActivatedTime()), () -> assertEquals(agreement.getUpdateTime(), read.getUpdateTime()),
            () -> assertEquals(1, agreementRepository.findByClientId(AgreementBuilder.CLIENT_ID).size()),
            () -> assertEquals(List.of(), agreementRepository.findByLocalPartnerId(AgreementBuilder.CLIENT_ID)),
            () -> assertThrows(ConcurrentRequestException.class,
                () -> agreementRepository.updateStatus(stale, AgreementStatus.CANCELLED, LATER)),
            () -> assertThrows(ConcurrentRequestException.class, () -> agreementRepository.updateLifecycle(stale,
                AgreementStatus.ACTIVE, null, LATER, LATER, LATER)),
            () -> assertThrows(DuplicateKeyException.class,
                () -> agreementRepository.insert(AgreementBuilder.template().agreementId(AGREEMENT_ID + 1).build())));
    }

    /** 签署结果每个角色行只登记一次，同一接受事件编号可读回整组角色。 */
    @Test
    void test_partyAcceptanceRecordedOnce() {
        AgreementParty client = AgreementPartyBuilder.template().build();
        AgreementParty entity = AgreementPartyBuilder.template().agreementPartyId(AgreementPartyBuilder.AGREEMENT_PARTY_ID + 1)
            .partyRole(AgreementPartyPartyRole.OPERATING_ENTITY).build();
        partyRepository.insertAll(List.of(client, entity));
        AgreementParty stale = partyRepository.findByUniqueKey(AGREEMENT_ID, AgreementPartyBuilder.PARTY_ID,
            AgreementPartyPartyRole.CLIENT);

        partyRepository.recordAcceptance(client, acceptance(1), LATER);
        partyRepository.recordAcceptance(entity, acceptance(null), LATER);

        List<AgreementParty> group = partyRepository.findByAcceptanceReference("WRITE-ACCEPT-1");
        assertAll(() -> assertEquals(2, group.size()), () -> assertEquals(1, group.get(0).getAcceptanceRefSlot()),
            () -> assertNull(group.get(1).getAcceptanceRefSlot()),
            () -> assertEquals(AgreementPartyAcceptanceMethod.CLICK_ACCEPTANCE, group.get(0).getAcceptanceMethod()),
            () -> assertEquals(LATER, group.get(0).getAcceptedTime()), () -> assertEquals(LATER, client.getUpdateTime()),
            () -> assertEquals(2, partyRepository.findByAgreementId(AGREEMENT_ID).size()),
            () -> assertThrows(ConcurrentRequestException.class,
                () -> partyRepository.recordAcceptance(stale, acceptance(1), LATER.plusSeconds(1))),
            () -> assertThrows(DuplicateKeyException.class, () -> partyRepository.insertAll(
                List.of(AgreementPartyBuilder.template().agreementPartyId(AgreementPartyBuilder.AGREEMENT_PARTY_ID + 2).build()))));
    }

    /** 文件引用与覆盖 OE 按唯一键与父读取；覆盖关系删除后再次删除报并发变化。 */
    @Test
    void test_documentsAndEntities() {
        documentRepository.insertAll(List.of(AgreementDocumentRefBuilder.template().build()));
        AgreementOperatingEntity first = AgreementOperatingEntityBuilder.template().build();
        entityRepository.insertAll(List.of(first, AgreementOperatingEntityBuilder.template()
            .agreementOperatingEntityId(AgreementOperatingEntityBuilder.AGREEMENT_OPERATING_ENTITY_ID + 1).entityNo("AE994").build()));

        AgreementDocumentRef document = documentRepository.findByUniqueKey(AGREEMENT_ID,
            AgreementDocumentRefDocumentType.SIGNING_DOCUMENT, AgreementDocumentRefBuilder.FILE_ID);
        assertAll(() -> assertEquals(64, document.getContentHash().length()),
            () -> assertEquals(1, documentRepository.findByAgreementId(AGREEMENT_ID).size()),
            () -> assertThrows(DuplicateKeyException.class, () -> documentRepository.insertAll(List.of(AgreementDocumentRefBuilder
                .template().agreementDocumentId(AgreementDocumentRefBuilder.AGREEMENT_DOCUMENT_ID + 1).build()))),
            () -> assertEquals(2, entityRepository.findByAgreementIds(List.of(AGREEMENT_ID)).size()),
            () -> assertEquals(List.of(), entityRepository.findByAgreementIds(List.of())),
            () -> assertEquals(first.getAgreementOperatingEntityId(),
                entityRepository.findByUniqueKey(AGREEMENT_ID, AgreementBuilder.ENTITY_NO).getAgreementOperatingEntityId()));

        entityRepository.delete(first);

        assertAll(() -> assertEquals(List.of("AE994"),
            entityRepository.findByAgreementId(AGREEMENT_ID).stream().map(AgreementOperatingEntity::getEntityNo).toList()),
            () -> assertThrows(ConcurrentRequestException.class, () -> entityRepository.delete(first)));
    }

    /** 一份点击接受的签署字段载体；slot 只在组内最小主键行为 1。 */
    private static AgreementParty acceptance(Integer slot) {
        AgreementParty acceptance = new AgreementParty();
        acceptance.setAcceptanceMethod(AgreementPartyAcceptanceMethod.CLICK_ACCEPTANCE);
        acceptance.setAcceptedByActorId(AgreementBuilder.ACTOR_ID);
        acceptance.setAcceptedTime(LATER);
        acceptance.setEvidenceDocumentId(AgreementDocumentRefBuilder.AGREEMENT_DOCUMENT_ID);
        acceptance.setAcceptanceReference("WRITE-ACCEPT-1");
        acceptance.setAcceptanceRefSlot(slot);
        acceptance.setAcceptanceRecordedBy(AgreementBuilder.ACTOR_ID);
        acceptance.setAcceptanceRecordedAt(LATER);
        return acceptance;
    }
}
