package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_agreement_document_ref 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class AgreementDocumentRefBuilder {
    /** 默认文件引用 ID */
    public static final long AGREEMENT_DOCUMENT_ID = 930000000000021L;

    /** 默认合同文件 */
    public static final long FILE_ID = 930000000000905L;

    private final AgreementDocumentRef agreementDocumentRef = new AgreementDocumentRef();

    private AgreementDocumentRefBuilder() {
    }

    /**
     * 默认合同的签署文件引用。
     *
     * @return 新 Builder
     */
    public static AgreementDocumentRefBuilder template() {
        return new AgreementDocumentRefBuilder()
            .agreementDocumentId(AGREEMENT_DOCUMENT_ID)
            .agreementId(AgreementBuilder.AGREEMENT_ID)
            .documentType(AgreementDocumentRefDocumentType.SIGNING_DOCUMENT)
            .fileId(FILE_ID)
            .contentHash("a".repeat(64))
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置文件引用标识。 */
    public AgreementDocumentRefBuilder agreementDocumentId(Long value) {
        agreementDocumentRef.setAgreementDocumentId(value);
        return this;
    }

    /** 设置所属合同。 */
    public AgreementDocumentRefBuilder agreementId(Long value) {
        agreementDocumentRef.setAgreementId(value);
        return this;
    }

    /** 设置文件用途。 */
    public AgreementDocumentRefBuilder documentType(AgreementDocumentRefDocumentType value) {
        agreementDocumentRef.setDocumentType(value);
        return this;
    }

    /** 设置Platform Support 合同文件逻辑引用。 */
    public AgreementDocumentRefBuilder fileId(Long value) {
        agreementDocumentRef.setFileId(value);
        return this;
    }

    /** 设置合同文件 SHA-256（64 位小写十六进制）。 */
    public AgreementDocumentRefBuilder contentHash(String value) {
        agreementDocumentRef.setContentHash(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public AgreementDocumentRefBuilder createTime(Instant value) {
        agreementDocumentRef.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public AgreementDocumentRefBuilder updateTime(Instant value) {
        agreementDocumentRef.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public AgreementDocumentRef build() {
        return agreementDocumentRef;
    }
}
