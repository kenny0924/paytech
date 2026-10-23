package com.paytech.platform.support.filedocument.domainservice.service;

import java.util.List;

import com.paytech.platform.support.filedocument.domain.storage.ChangeScanPolicyRevisionCommand;
import com.paytech.platform.support.filedocument.domain.storage.FileScanPolicyDetailView;
import com.paytech.platform.support.filedocument.domain.storage.FileScanPolicyView;
import com.paytech.platform.support.filedocument.domain.storage.FileStorageChange;
import com.paytech.platform.support.filedocument.domain.storage.SaveScanPolicyRevisionCommand;

/**
 * 检查策略（t_file_scan_policy、t_file_scan_requirement）的本库规则与保存；Owner 为 file-document
 * <li>写方法在调用方事务内锁定该代码的全部修订、核对状态与版本后条件更新，返回审计差异；不访问外部依赖。</li>
 * <li>同一代码最多一个 ACTIVE（active_slot=1）和一个 DRAFT；激活在同一事务内先停用原 ACTIVE 再启用草稿。</li>
 */
public interface FileScanPolicyService {

    /** 每个策略代码一行，按代码升序。 */
    List<FileScanPolicyView> listPolicies();

    /** 策略详情；代码不存在返回 404。 */
    FileScanPolicyDetailView policy(String policyCode);

    /** 新建策略代码及修订 1（DRAFT）与标准格式检查要求；代码已存在返回 400。 */
    FileStorageChange createPolicy(SaveScanPolicyRevisionCommand command);

    /** 以 ACTIVE 修订为模板新建 DRAFT 修订；没有 ACTIVE 返回 400，已有 DRAFT 返回 409。 */
    FileStorageChange createRevision(SaveScanPolicyRevisionCommand command);

    /** 按原版本修改 DRAFT 修订；无变化时版本不变。 */
    FileStorageChange updateRevision(SaveScanPolicyRevisionCommand command);

    /** 按原版本物理删除 DRAFT 修订及其检查要求。 */
    FileStorageChange discardRevision(ChangeScanPolicyRevisionCommand command);

    /** 按原版本激活 DRAFT 修订，原 ACTIVE 修订改为 RETIRED。 */
    FileStorageChange activateRevision(ChangeScanPolicyRevisionCommand command);

    /** 按原版本停用 ACTIVE 修订；有写入路由引用该代码时返回 400。 */
    FileStorageChange retire(ChangeScanPolicyRevisionCommand command);
}
