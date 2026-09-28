package com.paytech.risk.compliance.core.dal.dataobject;
import java.time.Instant;
/**
 * @param programId 程序身份
 * @param programVersionId 生效修订身份
 * @param versionNo 修订序号
 * @param activeSlot 生效占位，必须为 1
 * @param contentHash 经批准闭包摘要
 * @param approvedBy 批准人
 * @param approvedTime 批准时间
 */
public record MaterialProgram(long programId,long programVersionId,int versionNo,Integer activeSlot,String contentHash,Long approvedBy,Instant approvedTime) {}
