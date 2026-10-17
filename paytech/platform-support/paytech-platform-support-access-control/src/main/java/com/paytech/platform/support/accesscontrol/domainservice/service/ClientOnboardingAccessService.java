package com.paytech.platform.support.accesscontrol.domainservice.service;

/** 向文件用例发布 IAM 的窄授权能力，不开放 IAM 表或 Mapper。 */
public interface ClientOnboardingAccessService {
    /** 私网 CCT 查询仅接受当前有效客户账号与原 Tenant 权限，不代替 CCT 的资源检查。 */
    void readFile(long accountId,long expectedClientId,long tenantId);

    /** 在调用者事务中锁定账号并核验 Session 与唯一 PENDING 工作区，返回准确 Tenant。 */
    long lockUploadTenant(long accountId, long sessionId, long expectedClientId);
    /** 当前有效 Owner 可重放原 Tenant 的已成功回执；不授予新的上传权限。 */
    void lockFileRead(long accountId,long sessionId,long expectedClientId,long tenantId);
}
