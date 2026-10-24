package com.paytech.platform.support.service.processor.files;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Constraints;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;
import com.paytech.platform.support.filedocument.domainservice.service.FileUploadService;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.service.processor.files.request.QueryFileUploadConstraintsRequest;
import com.paytech.platform.support.service.processor.files.response.QueryFileUploadConstraintsResponse;

import lombok.AllArgsConstructor;

/**
 * 查询调用方在某一访问级别开始上传时生效的写入限制：按 (来源系统, 访问级别) 路由选择存储 → 读取扫描策略唯一的有效修订
 * <li>与 {@link StartFileUploadProcessor} 使用同一份路由解析与修订读取，提示与开始上传的实际结果一致。</li>
 * <li>只读、不加事务、不写库、不签发授权，也不连接存储。</li>
 * <li>没有可用路由、桶或实例，或策略没有唯一有效修订时，上传会返回 503；这里改为返回未配置，页面据此提示并禁用上传。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class QueryFileUploadConstraintsProcessor
    extends AbstractProcessor<QueryFileUploadConstraintsRequest, QueryFileUploadConstraintsResponse> {

    private final Clock clock;

    private final FileUploadService files;

    private final FileStorageRegistry registry;

    @Override
    protected QueryFileUploadConstraintsResponse processRequest(QueryFileUploadConstraintsRequest request) {
        // 按来源系统与访问级别路由选择存储
        StorageWriteTarget target = target(request);
        if (target == null) {
            return unconfigured(request);
        }

        // 读取扫描策略的有效修订
        Constraints constraints = constraints(target);
        if (constraints == null) {
            return unconfigured(request);
        }
        return new QueryFileUploadConstraintsResponse(request.getAccessLevel(), target, constraints);
    }

    /** 开始上传使用的写入目标；不可用为空。 */
    StorageWriteTarget target(QueryFileUploadConstraintsRequest request) {
        try {
            return registry.forWrite(request.getSourceSystem(), request.getAccessLevel());
        } catch (ServiceUnavailableException unavailable) {
            return null;
        }
    }

    /** 写入目标冻结的扫描策略当前有效修订的限制；不可用为空。 */
    Constraints constraints(StorageWriteTarget target) {
        try {
            return files.constraints(target.getScanPolicyCode(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        } catch (ServiceUnavailableException unavailable) {
            return null;
        }
    }

    /** 未配置：只回显访问级别。 */
    QueryFileUploadConstraintsResponse unconfigured(QueryFileUploadConstraintsRequest request) {
        return new QueryFileUploadConstraintsResponse(request.getAccessLevel(), null, null);
    }
}
