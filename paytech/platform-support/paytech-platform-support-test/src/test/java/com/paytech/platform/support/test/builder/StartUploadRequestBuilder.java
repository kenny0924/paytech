package com.paytech.platform.support.test.builder;

import com.paytech.platform.support.api.onboardingfiles.request.StartUploadRequestBody;

/**
 * 开始上传请求的合法默认值：指定申请主体上的 PDF 材料，大小与 {@link OnboardingFileBuilder#PDF} 一致。
 *
 * <p>只构造正式 DTO，不修复负例；任一字段可显式设为 null 或非法值。
 */
public final class StartUploadRequestBuilder {

    /** 默认展示文件名。 */
    public static final String DEFAULT_FILENAME = "certificate-of-incorporation.pdf";

    /** 默认声明类型。 */
    public static final String DEFAULT_MIME = "application/pdf";

    private String applicationId;
    private String subjectPartyId;
    private String filename = DEFAULT_FILENAME;
    private String mimeType = DEFAULT_MIME;
    private String sizeBytes = Long.toString(OnboardingFileBuilder.PDF.length);

    private StartUploadRequestBuilder(String applicationId, String subjectPartyId) {
        this.applicationId = applicationId;
        this.subjectPartyId = subjectPartyId;
    }

    /** 指定申请主体上的默认 PDF 材料。 */
    public static StartUploadRequestBuilder template(long applicationId, long subjectPartyId) {
        return new StartUploadRequestBuilder(Long.toString(applicationId), Long.toString(subjectPartyId));
    }

    public StartUploadRequestBuilder applicationId(String applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    public StartUploadRequestBuilder subjectPartyId(String subjectPartyId) {
        this.subjectPartyId = subjectPartyId;
        return this;
    }

    public StartUploadRequestBuilder filename(String filename) {
        this.filename = filename;
        return this;
    }

    public StartUploadRequestBuilder mimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public StartUploadRequestBuilder sizeBytes(String sizeBytes) {
        this.sizeBytes = sizeBytes;
        return this;
    }

    /** 构建正式请求 DTO。 */
    public StartUploadRequestBody build() {
        return new StartUploadRequestBody(applicationId, subjectPartyId, filename, mimeType, sizeBytes);
    }
}
