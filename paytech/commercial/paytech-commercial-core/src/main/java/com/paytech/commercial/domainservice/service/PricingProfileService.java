package com.paytech.commercial.domainservice.service;

import java.util.List;

import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.domain.PricingProfileContext;
import com.paytech.commercial.domain.PricingProfileView;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.PricingProfileStatus;

/**
 * 定价画像的本地写入与列表读取（technique.md §2.2）
 * <li>写方法接收已校验的 {@link PricingProfileContext}，在一个本地事务内只写画像、条件行与画像 BCR；条件行不单独审计，随 Root 快照留档。</li>
 */
public interface PricingProfileService {
    /**
     * 创建 DRAFT 画像、条件行与 Root Version 1 CREATE 快照；成功后回填 context.root
     *
     * @param context 创建上下文
     */
    void createProfile(PricingProfileContext context);

    /**
     * DRAFT 内改写名称与 Payment Method、整体替换条件行，Root CAS 并写 UPDATE 快照
     *
     * @param context 修改上下文
     */
    void updateDraft(PricingProfileContext context);

    /**
     * 按 context.nextStatus 条件更新状态（激活 / 退役），Root CAS 并写 UPDATE 快照
     *
     * @param context 状态推进上下文
     * @param reason 业务原因
     */
    void changeStatus(PricingProfileContext context, BusinessChangeReason reason);

    /**
     * 在同一只读快照内按种类与状态读取画像、条件行与 Root 版本
     *
     * @param kind 种类，null 不限
     * @param status 状态，null 不限
     * @return 按画像 ID 升序的画像
     */
    List<PricingProfileView> listProfiles(PricingProfileKind kind, PricingProfileStatus status);
}
