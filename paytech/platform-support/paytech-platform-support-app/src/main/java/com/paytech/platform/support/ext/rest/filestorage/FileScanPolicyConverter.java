package com.paytech.platform.support.ext.rest.filestorage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.filestorage.request.ActivateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.DiscardScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.request.RetireScanPolicyRequestBody;
import com.paytech.platform.support.api.filestorage.request.UpdateScanPolicyRevisionRequestBody;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyDetailResponseBody;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyListResponseBody;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyRequirement;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyRevision;
import com.paytech.platform.support.api.filestorage.response.ScanPolicyRouteRef;
import com.paytech.platform.support.api.filestorage.response.ScanPolicySummary;
import com.paytech.platform.support.filedocument.domain.storage.FileScanPolicyDetailView;
import com.paytech.platform.support.filedocument.domain.storage.FileScanPolicyView;
import com.paytech.platform.support.service.processor.filestorage.request.ChangeScanPolicyRevisionRequest;
import com.paytech.platform.support.service.processor.filestorage.request.SaveScanPolicyRequest;
import com.paytech.platform.support.service.processor.filestorage.request.ScanPolicyQueryRequest;
import com.paytech.platform.support.service.processor.filestorage.response.ScanPolicyCatalog;

/** 检查策略的协议转换：字节数在 Wire 上是十进制字符串（Body 注解已约束格式），文本去除首尾空白。 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class FileScanPolicyConverter {

    /** 列表或详情查询。 */
    ScanPolicyQueryRequest query(String actor, String session, String policyCode) {
        return new ScanPolicyQueryRequest(actor, session, policyCode);
    }

    /** 新建策略。 */
    SaveScanPolicyRequest create(String actor, String session, CreateScanPolicyRequestBody body) {
        return new SaveScanPolicyRequest(actor, session, body.getPolicyCode(), 0, body.getAllowedMimeTypes(),
            Long.parseLong(body.getMaxSizeBytes()), 0, text(body.getReason()));
    }

    /** 以 ACTIVE 为模板新建修订；内容取 ACTIVE 修订。 */
    SaveScanPolicyRequest createRevision(String actor, String session, String policyCode,
        CreateScanPolicyRevisionRequestBody body) {
        return new SaveScanPolicyRequest(actor, session, policyCode, 0, null, 0, 0, text(body.getReason()));
    }

    /** 修改草稿。 */
    SaveScanPolicyRequest updateRevision(String actor, String session, String policyCode, int revisionNo,
        UpdateScanPolicyRevisionRequestBody body) {
        return new SaveScanPolicyRequest(actor, session, policyCode, revisionNo, body.getAllowedMimeTypes(),
            Long.parseLong(body.getMaxSizeBytes()), body.getExpectedVersion(), text(body.getReason()));
    }

    /** 废弃草稿。 */
    ChangeScanPolicyRevisionRequest discard(String actor, String session, String policyCode, int revisionNo,
        DiscardScanPolicyRevisionRequestBody body) {
        return new ChangeScanPolicyRevisionRequest(actor, session, policyCode, revisionNo, body.getExpectedVersion(),
            text(body.getReason()));
    }

    /** 激活草稿。 */
    ChangeScanPolicyRevisionRequest activate(String actor, String session, String policyCode, int revisionNo,
        ActivateScanPolicyRevisionRequestBody body) {
        return new ChangeScanPolicyRevisionRequest(actor, session, policyCode, revisionNo, body.getExpectedVersion(),
            text(body.getReason()));
    }

    /** 停用 ACTIVE 修订。 */
    ChangeScanPolicyRevisionRequest retire(String actor, String session, String policyCode,
        RetireScanPolicyRequestBody body) {
        return new ChangeScanPolicyRevisionRequest(actor, session, policyCode, null, body.getExpectedVersion(),
            text(body.getReason()));
    }

    /** 策略列表与格式检查器能识别的类型。 */
    ScanPolicyListResponseBody list(ScanPolicyCatalog catalog) {
        return new ScanPolicyListResponseBody(catalog.getPolicies().stream().map(this::summary).toList(),
            catalog.getSupportedMimeTypes());
    }

    /** 列表行：字节数输出十进制字符串。 */
    ScanPolicySummary summary(FileScanPolicyView view) {
        return new ScanPolicySummary(view.getPolicyCode(), view.getActiveRevisionNo(), view.getDraftRevisionNo(),
            view.getAllowedMimeTypes(), Long.toString(view.getMaxSizeBytes()), view.getRouteCount(), view.getStatus(),
            view.getUpdateTime());
    }

    /** 详情：引用路由与修订（含检查要求）。 */
    ScanPolicyDetailResponseBody detail(FileScanPolicyDetailView view) {
        return new ScanPolicyDetailResponseBody(view.getPolicyCode(),
            view.getRoutes().stream().map(route -> new ScanPolicyRouteRef(route.getSourceSystem(),
                route.getAccessLevel(), route.getInstanceCode(), route.getBucketName())).toList(),
            view.getRevisions().stream().map(this::revision).toList());
    }

    /** 一个修订。 */
    ScanPolicyRevision revision(FileScanPolicyDetailView.Revision revision) {
        return new ScanPolicyRevision(revision.getRevisionNo(), revision.getStatus(), revision.getAllowedMimeTypes(),
            Long.toString(revision.getMaxSizeBytes()),
            revision.getRequirements().stream().map(rule -> new ScanPolicyRequirement(rule.getScannerCode(),
                rule.getScanType(), rule.getMinimumScannerVersion(), rule.getAcceptedResultCodes())).toList(),
            revision.getVersion(), revision.getCreateTime(), revision.getUpdateTime());
    }

    /** 去除首尾空白；空值由内部请求注解拒绝。 */
    String text(String value) {
        return value == null ? null : value.strip();
    }
}
