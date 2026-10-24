package com.paytech.platform.support.filedocument.core.dal.dataobject;

/**
 * 在创建时间有效的扫描策略最小投影。
 * @param policyId 将冻结到 Upload 的策略身份
 * @param ruleSchemaVersion MIME 规则结构版本，当前只解析 1
 * @param allowedMimeRules 有界 JSON 对象，mimeTypes 为非空 MIME 数组
 * @param maxSizeBytes 最大允许字节数
 * @param requirementCount 该修订必须执行的扫描要求数量，零项不允许上传
 * @param revisionNo 修订号；列顺序与 Mapper XML 的 SELECT 一致（按构造参数顺序映射）
 */
public record FileScanPolicy(long policyId, int ruleSchemaVersion, String allowedMimeRules,
    long maxSizeBytes, int requirementCount, int revisionNo) {}
