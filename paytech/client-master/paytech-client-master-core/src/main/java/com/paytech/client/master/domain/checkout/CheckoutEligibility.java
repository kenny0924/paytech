package com.paytech.client.master.domain.checkout;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.Client;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.ClientBusinessClassification;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.PartyProfile;

import lombok.Value;
import lombok.experimental.Accessors;

/** 一次候选查询的完整主档结果，由入站 Converter 投影为响应。 */
@Value
@Accessors(fluent = true)
public class CheckoutEligibility {
    /** 原 Client。 */
    Client client;
    /** Client 主体资料；分类校验已保证存在且已批准。 */
    PartyProfile clientProfile;
    /** 商业时点的唯一 Primary 分类。 */
    ClientBusinessClassification classification;
    /** 按 Affiliation 优先级升序的候选。 */
    List<EntityCandidate> candidates;
    /** 全部 Affiliation 的 OE 编号。 */
    List<String> entityNos;
    /** 全部 PARTNER 来源 Local Partner ID。 */
    List<Long> localPartnerIds;
    /** 已校验的业务根，按类型与记录 ID 排序。 */
    List<BusinessChangeRecord> roots;
    /** Client 已存卡开关。 */
    boolean storedCardEnabled;
}
