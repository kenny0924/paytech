package com.paytech.risk.compliance.domain;
import java.util.List;
/**
 * @param programId 精确程序
 * @param programVersionId 精确生效修订
 * @param items 同一只读快照下的材料要求
 */
public record MaterialRequirements(long programId,long programVersionId,List<Item> items) {
    /**
     * @param requirementCode 要求代码
     * @param documentType 材料类型
     * @param requiredFlag 是否必需
     */
    public record Item(String requirementCode,String documentType,boolean requiredFlag) {}
}
