package com.paytech.client.master.core.dal.dataobject;
/**
 * @param documentId 材料引用
 * @param subjectPartyId 原材料主体
 * @param requirementCode 材料要求
 * @param documentType 材料类型
 * @param fileId 原 Platform 文件
 * @param contentHash 固定字节摘要
 */
public record ClientApplicationDocument(long documentId,long subjectPartyId,String requirementCode,String documentType,long fileId,String contentHash) {
    /** 摘要不进入普通日志。 */
    @Override public String toString() { return "ClientApplicationDocument[REDACTED]"; }
}
