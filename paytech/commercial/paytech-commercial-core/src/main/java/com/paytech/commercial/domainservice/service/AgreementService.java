package com.paytech.commercial.domainservice.service;

import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domain.AgreementDetailView;
import com.paytech.commercial.domain.enums.BusinessChangeReason;

/**
 * 合同聚合的本地写入与详情读取（technique.md §2.2）
 * <li>写方法接收 Processor 已校验并构造完整的 {@link AgreementContext}，在一个本地事务内只写本库业务表、合同 BCR（Root CAS 与受审计
 * Child 快照）与 compensation_event；不重复校验已确认的归属与关系，不访问外部依赖。任一 SQL、行数或 CAS 失败整体回滚。</li>
 */
public interface AgreementService {
    /**
     * 创建合同：合同行、参与方、文件、覆盖 OE 与 Root Version 1 CREATE 快照；成功后回填 context.root
     *
     * @param context 创建上下文
     */
    void createAgreement(AgreementContext context);

    /**
     * 推进合同生命周期（提交签署、激活、终止）：登记新增文件、条件更新状态与时间、Root CAS 与快照；
     * context.statusChangedEvent 非空时在同一事务登记发布任务
     *
     * @param context 带目标状态的上下文
     * @param reason 业务原因
     */
    void changeLifecycle(AgreementContext context, BusinessChangeReason reason);

    /**
     * 登记一个 Party 全部角色的签署结果：组内最小主键行占 acceptance_ref_slot=1；context.nextStatus 为 SIGNED 时同时推进合同
     *
     * @param context 签署上下文
     */
    void recordAcceptance(AgreementContext context);

    /**
     * 替换订阅产品：Root CAS 后为删除行先写 DELETE 快照再删除，为新增行插入后写 CREATE 快照
     *
     * @param context 订阅替换上下文
     */
    void replaceProducts(AgreementContext context);

    /**
     * 新增开通项与约束行，Root CAS 并写开通项 CREATE 快照
     *
     * @param context 开通项上下文
     */
    void addCapability(AgreementContext context);

    /**
     * 按 context.capabilityChange 改写开通项状态与条款、整体替换约束行，Root CAS 并写开通项 UPDATE 快照
     *
     * @param context 开通项上下文
     */
    void updateCapability(AgreementContext context);

    /**
     * 在同一只读快照内读取合同详情与当前 Root 版本，保证调用方据此提交的 expectedChangeVersion 对应所见内容
     *
     * @param agreementId 合同 ID
     * @return 合同详情；合同不存在抛出业务检查失败
     */
    AgreementDetailView getAgreementDetail(long agreementId);
}
