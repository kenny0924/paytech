package com.paytech.platform.support.service.processor;

import java.time.Duration;
import java.util.List;

/**
 * 入驻文件写入口共用的资源锁参数
 * <li>等待与租约沿用本服务注解锁配置 500ms / 30s，由 LockCommand 显式提供，不读取注解锁配置。</li>
 * <li>Key 保持原注解锁的 {@code file:upload:complete:<uploadId>} 与 {@code file:upload:start:<accountId>:<applicationId>:<subjectPartyId>}
 * 互斥域，滚动发布期间新旧实例仍对同一资源互斥。</li>
 */
final class OnboardingFileLocks {

    /** 获取资源锁的最长等待时间，超时返回并发冲突。 */
    static final Duration WAIT_TIME = Duration.ofMillis(500);

    /** 资源锁的有限租约，覆盖开始或完成流程中的短事务、CCT 授权与一次存储调用。 */
    static final Duration LEASE_TIME = Duration.ofSeconds(30);

    /** 仅提供锁参数。 */
    private OnboardingFileLocks() { }

    /** 开始上传的唯一锁 Key：同一账号对同一申请主体的上传目标串行登记。 */
    static List<String> startKeys(long accountId, long applicationId, long subjectPartyId) {
        return List.of("file:upload:start:" + accountId + ":" + applicationId + ":" + subjectPartyId);
    }

    /** 完成上传的唯一锁 Key：一次上传只产生一个文件。 */
    static List<String> completionKeys(long uploadId) {
        return List.of("file:upload:complete:" + uploadId);
    }
}
