package com.paytech.client.master.domain.onboarding;
import com.paytech.client.master.domain.enums.ClientType;
/**
 * 一次材料关联的目标：申请、主体及申请冻结的程序解析维度
 *
 * @param applicationId 原申请
 * @param clientId 原 Client
 * @param subjectPartyId 原主体
 * @param primary 是否为申请主主体；否则为组织关联人员
 * @param jurisdictionCode 申请辖区
 * @param partyType 申请主主体类型
 * @param responsiblePartnerId 责任 PayFac；平台自营为空
 */
public record ClientMaterialTarget(long applicationId,long clientId,long subjectPartyId,boolean primary,String jurisdictionCode,
    ClientType partyType,Long responsiblePartnerId) {}
