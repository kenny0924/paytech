package com.paytech.foundation.compensation.api;

/**
 * 扫描任务提交摘要。每条记录仍由已接受的工作任务在内部执行认领。
 *
 * @param candidateCount
 *            数据库候选记录数
 * @param submittedCount
 *            有界执行器已接受的数量
 * @param rejectedCount
 *            保持未修改、等待后续扫描的数量
 * @author Codex
 */
public record CompensationScanResult(int candidateCount, int submittedCount, int rejectedCount) {
}
