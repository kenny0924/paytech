package com.paytech.ledger.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;

/** t_accounting_rule_bundle_ver：规则版本持久化。 */
@Mapper
public interface AccountingRuleBundleVerMapper {

    /** 插入版本。 */
    int insert(AccountingRuleBundleVer record);

    /** 按主键读取。 */
    AccountingRuleBundleVer selectById(@Param("id") long id);

    /** 按规则集与版本号读取。 */
    AccountingRuleBundleVer selectByBundleVer(@Param("bundleId") long bundleId, @Param("bundleVer") long bundleVer);

    /** 按规则集与版本号读取并加排他锁。 */
    AccountingRuleBundleVer selectByBundleVerForUpdate(@Param("bundleId") long bundleId,
        @Param("bundleVer") long bundleVer);

    /** 规则集全部版本，按版本号降序。 */
    List<AccountingRuleBundleVer> selectByBundle(@Param("bundleId") long bundleId);

    /** 已到生效时间的 APPROVED 版本，按生效时间、规则集、版本号升序。 */
    List<AccountingRuleBundleVer> selectDueApproved(@Param("asOf") Instant asOf);

    /** 规则集的下一个版本号。 */
    long selectNextBundleVer(@Param("bundleId") long bundleId);

    /** 统计规则集中 DRAFT 或 PENDING_APPROVAL 版本数。 */
    long countOpenVersions(@Param("bundleId") long bundleId);

    /**
     * 以原状态为条件迁移状态，同时写入复核人、批准时间与失效时间
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("ver") AccountingRuleBundleVer ver, @Param("status") RuleVersionStatus status,
        @Param("checkerId") Long checkerId, @Param("approvedTime") Instant approvedTime,
        @Param("effectiveTo") Instant effectiveTo, @Param("now") Instant now);
}
