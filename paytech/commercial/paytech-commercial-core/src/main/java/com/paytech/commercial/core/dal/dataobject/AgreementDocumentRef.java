package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;

import lombok.Getter;
import lombok.Setter;

/**
 * t_agreement_document_ref：合同文件引用。
 * <li>插入后冻结，不更新、不删除；file_id 与 content_hash 成组。</li>
 */
@Getter
@Setter
public class AgreementDocumentRef {
    /** 文件引用标识 */
    private Long agreementDocumentId;

    /** 所属合同 */
    private Long agreementId;

    /** 文件用途 */
    private AgreementDocumentRefDocumentType documentType;

    /** Platform Support 合同文件逻辑引用 */
    private Long fileId;

    /** 合同文件 SHA-256（64 位小写十六进制） */
    private String contentHash;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
