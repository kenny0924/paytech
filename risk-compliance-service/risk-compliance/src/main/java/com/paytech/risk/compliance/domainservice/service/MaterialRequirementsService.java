package com.paytech.risk.compliance.domainservice.service;
import java.time.Instant;
import com.paytech.risk.compliance.domain.MaterialRequirements;
/** 经映射选定程序后读取完整有界的材料目录。 */
public interface MaterialRequirementsService {
    /** 调用者在同一本地只读事务中执行，不代表审核通过。 */
    MaterialRequirements read(String programCode,String qualificationType,Instant now);
}
