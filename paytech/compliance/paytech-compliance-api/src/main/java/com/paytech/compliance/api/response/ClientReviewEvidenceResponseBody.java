package com.paytech.compliance.api.response;

import lombok.Getter;
import lombok.Setter;

/** 一份证据引用及核验状态。 */
@Getter
@Setter
public class ClientReviewEvidenceResponseBody {

    /** 证据 ID。 */
    private String evidenceId;

    /** 文件服务地址 /files/v1/{fileId}。 */
    private String fileAddress;

    /** 文件 SHA-256。 */
    private String contentHash;

    /** 材料类型代码。 */
    private String documentType;

    /** 签发日期 YYYY-MM-DD；未提供为空。 */
    private String issueDate;

    /** 到期日期 YYYY-MM-DD；未提供为空。 */
    private String expiryDate;

    /** RECEIVED、ACCEPTED、REJECTED 或 SUPERSEDED。 */
    private String status;

    /** 拒绝原因码；未拒绝为空。 */
    private String rejectReasonCode;

    /** 核验人；未核验为空。 */
    private String reviewerId;

    /** 核验时间；未核验为空。 */
    private String reviewedAt;

    /** 从上一轮案件复用时的来源案件 ID；否则为空。 */
    private String reusedFromCaseId;
}
