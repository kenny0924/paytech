package com.paytech.risk.compliance.core.dal.dataobject;
/**
 * @param requirementCode 要求代码
 * @param documentType 材料类型
 * @param requiredFlag 必需标记，只接受 0 或 1
 */
public record MaterialRequirement(String requirementCode,String documentType,int requiredFlag) {}
