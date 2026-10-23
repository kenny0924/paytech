package com.paytech.platform.support.core.integration.localization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;
import com.paytech.platform.support.filedocument.integration.storage.StoredContentType;
import com.paytech.platform.support.localization.integration.ReleaseBundleStore;
import com.paytech.platform.support.service.processor.files.CompleteFileUploadProcessor;
import com.paytech.platform.support.service.processor.files.StartFileUploadProcessor;
import com.paytech.platform.support.service.processor.files.request.CompleteFileUploadRequest;
import com.paytech.platform.support.service.processor.files.request.StartFileUploadRequest;
import com.paytech.platform.support.service.processor.files.response.CompleteFileUploadResponse;
import com.paytech.platform.support.service.processor.files.response.StartFileUploadResponse;

import lombok.AllArgsConstructor;

/**
 * 语言包写入与公开地址：经 Platform 进程内文件入口上传到公开桶（技术方案 3.3 第 1 步、3.7）
 * <li>与内部文件接口走同一条生产链路：{@link StartFileUploadProcessor} 按来源系统 platform-support × PUBLIC 的写入路由登记上传并签发
 * PUT 授权，{@link FileStorageRegistry#putThroughGrant} 按授权写入字节，{@link CompleteFileUploadProcessor} 核对存储并登记文件与
 * FILE_SCAN 检查任务。检查策略由写入路由决定（登记为 I18N_BUNDLE_JSON）。</li>
 * <li>对象名 {@code i18n/应用/发布号/语言/命名空间/摘要前 16 位/随机部分}：首次上传授权带 If-None-Match，随机部分保证每个文件只写一次，
 * 重试或发布号复用都不会覆盖已有对象。</li>
 * <li>全部在发布事务外调用；任一步失败返回 503，已登记的上传或文件成为孤儿，不伪造地址。</li>
 * <li>公开地址由文件服务按桶的对外基础地址与对象名计算，只在检查通过后返回，不落库。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class FileServiceReleaseBundleStore implements ReleaseBundleStore {

    /** 上传原因码。 */
    public static final String REASON_CODE = "I18N_RELEASE_BUNDLE";

    /** 对象名前缀的根目录。 */
    static final String KEY_ROOT = "i18n";

    /** 对象名中保留的摘要位数；完整摘要另存于发布语言包行与文件记录。 */
    static final int KEY_DIGEST_LENGTH = 16;

    private final StartFileUploadProcessor starts;

    private final CompleteFileUploadProcessor completions;

    private final FileLifecycleService files;

    private final FileStorageRegistry registry;

    /** 开始上传 → 按授权写入 → 完成上传，核对文件服务登记的摘要与生成时一致。 */
    @Override
    public FileAddress store(String applicationCode, int releaseNo, String locale, String namespaceCode,
        byte[] content, String sha256) {
        // 登记公开上传并签发授权
        StartFileUploadResponse started = starts.process(new StartFileUploadRequest(FileSourceCodes.PLATFORM_SUPPORT,
            REASON_CODE, namespaceCode + ".json", StoredContentType.JSON, content.length, null, FileAccessLevel.PUBLIC,
            objectKeyPrefix(applicationCode, releaseNo, locale, namespaceCode, sha256)));

        // 按授权写入字节
        Upload upload = files.sourceUpload(started.getUploadId());
        registry.putThroughGrant(upload.storageProvider(), upload.containerRef(), started.getGrant(), content);

        // 完成上传并核对摘要
        CompleteFileUploadResponse completed = completions.process(
            new CompleteFileUploadRequest(FileSourceCodes.PLATFORM_SUPPORT, started.getUploadId()));
        FileAddress address = FileAddress.of(completed.getFileId());
        StoredFileView view = files.describe(completed.getFileId());
        if (!sha256.equals(view.getContentHash())) {
            throw new ErrorException("语言包登记摘要与生成时不一致: " + address.value() + ", 应用: " + applicationCode
                + ", 发布号: " + releaseNo + ", 语言: " + locale + ", 命名空间: " + namespaceCode);
        }
        return address;
    }

    /** 文件元数据中的公开地址；不是公开文件或检查尚未通过时没有地址，返回 503。 */
    @Override
    public String publicUrl(FileAddress fileAddress) {
        StoredFileView view = files.describe(fileAddress.fileId());
        if (view.getAccessLevel() != FileAccessLevel.PUBLIC || view.getPublicUrl() == null) {
            throw new ServiceUnavailableException();
        }
        return view.getPublicUrl();
    }

    /**
     * 对象名前缀：{@code i18n/应用/发布号/语言/命名空间/摘要前 16 位}。
     *
     * @param applicationCode 应用编码
     * @param releaseNo 发布号
     * @param locale 语言代码
     * @param namespaceCode 命名空间编码
     * @param sha256 内容摘要
     * @return 前缀
     */
    static String objectKeyPrefix(String applicationCode, int releaseNo, String locale, String namespaceCode,
        String sha256) {
        return String.join("/", KEY_ROOT, applicationCode, Integer.toString(releaseNo), locale, namespaceCode,
            sha256.substring(0, KEY_DIGEST_LENGTH));
    }
}
