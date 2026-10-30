package com.paytech.tax.integration.file;

/** 税票文件存入 Platform 文件服务（PRIVATE，reasonCode TAX_RECEIPT）；事务外调用。 */
public interface TaxDocumentFileClient {

    /**
     * 上传私有文件：开始上传 → PUT 内容 → 完成
     * <li>每次调用新建一次上传；失败抛出异常，由补偿任务重试同一单据（不重新取号）。</li>
     *
     * @param filename 文件名
     * @param mimeType 内容类型
     * @param content 文件字节
     * @return 文件地址 /files/v1/{fileId}
     */
    String uploadPrivate(String filename, String mimeType, byte[] content);
}
