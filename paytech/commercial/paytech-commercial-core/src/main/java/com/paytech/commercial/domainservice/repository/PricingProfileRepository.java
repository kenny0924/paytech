package com.paytech.commercial.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.PricingProfileStatus;

/** t_pricing_profile 的本地仓储；不负责其他表的加载或事务。 */
public interface PricingProfileRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 pricing_profile 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.PricingProfile> findForCheckout(CommercialCheckoutScope scope);

    /**
     * 插入定价画像；影响行数不为 1 时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param pricingProfile 已准备 ID 与全部字段的新行
     */
    void insert(PricingProfile pricingProfile);

    /**
     * 按主键读取。
     *
     * @param pricingProfileId 画像 ID
     * @return 定价画像，不存在为 null
     */
    PricingProfile findById(long pricingProfileId);

    /**
     * 按画像代码（uk_pricing_profile_code）读取。
     *
     * @param pricingProfileCode 画像代码
     * @return 定价画像，不存在为 null
     */
    PricingProfile findByCode(String pricingProfileCode);

    /**
     * 按一组画像代码读取；空集合不访问数据库。
     *
     * @param pricingProfileCodes 画像代码
     * @return 按 pricing_profile_id 升序的定价画像
     */
    List<PricingProfile> findByCodes(Collection<String> pricingProfileCodes);

    /**
     * 按种类与状态筛选画像，供运营选择；画像目录规模有限，不分页。
     *
     * @param profileKind 种类，null 不限
     * @param status 状态，null 不限
     * @return 按主键升序的画像
     */
    List<PricingProfile> findByKindAndStatus(PricingProfileKind profileKind, PricingProfileStatus status);

    /**
     * 以对象当前状态为条件迁移状态；未命中抛出并发冲突，成功后同步原对象的状态与更新时间。
     *
     * @param pricingProfile 已读取的行
     * @param status 目标状态
     * @param now 本次更新时间
     */
    void updateStatus(PricingProfile pricingProfile, PricingProfileStatus status, Instant now);

    /**
     * 改写画像可编辑内容；以对象当前状态与 update_time 为条件，未命中抛出并发冲突，成功后同步原对象。
     * <li>只有 DRAFT 可编辑由调用方校验；Code 创建后不可修改，不在此方法内。</li>
     *
     * @param profile 已读取的画像
     * @param profileName 新名称
     * @param profileKind 新种类
     * @param paymentMethodCode 新 Payment Method 代码，非 PAYMENT_METHOD 种类为空
     * @param now 本次更新时间
     */
    void updateDraft(PricingProfile profile, String profileName, PricingProfileKind profileKind, String paymentMethodCode,
        Instant now);
}
