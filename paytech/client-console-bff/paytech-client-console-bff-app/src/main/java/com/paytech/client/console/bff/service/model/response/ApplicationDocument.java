package com.paytech.client.console.bff.service.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 申请中的一条材料引用及其文件状态；文件无法查询时仅保留 CCT 引用。 */
@Getter
@AllArgsConstructor
public class ApplicationDocument {

    /** 材料 ID */
    private final String documentId;

    /** 材料所属主体 Party ID */
    private final String subjectPartyId;

    /** 对应的材料要求码 */
    private final String requirementCode;

    /** 材料类型 */
    private final String documentType;

    /** 文件 ID，由 CCT 保存的文件地址解析 */
    private final String fileId;

    /** CCT 保存的文件地址 /files/v1/{fileId} */
    private final String fileAddress;

    /** 所属主体是否仍在申请范围内 */
    private final boolean subjectInScope;

    /** 文件状态读取结果：AVAILABLE 或 UNAVAILABLE */
    private final String fileStatus;

    /**
     * 文件元数据
     * <li>fileStatus 为 UNAVAILABLE 时为空。</li>
     */
    private final OnboardingFileResponseBody file;
}
