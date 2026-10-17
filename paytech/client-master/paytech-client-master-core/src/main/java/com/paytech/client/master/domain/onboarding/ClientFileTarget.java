package com.paytech.client.master.domain.onboarding;

import com.paytech.client.master.domain.enums.ClientType;

/**
 * CCT 当前材料目标，不携带文件状态、Session 或其他 Owner 的授权结果。
 *
 * @param applicationId 原申请标识
 * @param clientId 申请所属 Client
 * @param subjectPartyId 被授权主体
 * @param clientType 原 Client 类型
 * @param canUpload 当前资料与关系允许上传
 * @param canRead 当前账号可以读取这个原目标
 */
public record ClientFileTarget(long applicationId, long clientId, long subjectPartyId,
                               ClientType clientType, boolean canUpload, boolean canRead) {}
