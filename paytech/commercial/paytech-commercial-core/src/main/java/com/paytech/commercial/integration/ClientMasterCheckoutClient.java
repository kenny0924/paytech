package com.paytech.commercial.integration;

import java.util.List;

import com.paytech.commercial.domain.CheckoutCandidateContext;
import com.paytech.commercial.domain.CheckoutQuery;
import com.paytech.commercial.domain.ClientMasterCandidates;
import com.paytech.commercial.domain.PartyProfileFacts;

/**
 * client-master 两段式解析端口（design/06 §2.1）；只能在数据库事务外调用。
 * <li>Client 不存在、不合格与主档配置冲突映射为原 Checkout 合同的同名业务错误；其他任何失败一律 503 OWNER_UNAVAILABLE。</li>
 */
public interface ClientMasterCheckoutClient {
    /**
     * 读取按优先级排序的主档候选及根版本。
     *
     * @param query 已校验的解析查询
     * @return 主档候选
     */
    ClientMasterCandidates candidates(CheckoutQuery query);

    /**
     * 外部资格检查后复核主档根版本与选定授权。
     *
     * @param query 原解析查询
     * @param master 首次主档候选
     * @param selected 选定候选
     * @return 未变化为 true
     */
    boolean unchanged(CheckoutQuery query, ClientMasterCandidates master, CheckoutCandidateContext selected);

    /**
     * 按资料 ID 读取合同参与方资料。
     *
     * @param ids 资料 ID；为空时不发起调用
     * @return 存在的资料
     */
    List<PartyProfileFacts> partyProfiles(List<Long> ids);
}
