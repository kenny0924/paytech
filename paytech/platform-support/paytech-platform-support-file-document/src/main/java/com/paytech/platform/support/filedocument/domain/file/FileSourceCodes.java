package com.paytech.platform.support.filedocument.domain.file;

/** 文件来源系统与上传原因中 Platform 自身依赖的稳定代码。 */
public final class FileSourceCodes {

    /** Platform 自行发起上传时的来源系统；也是 Platform 内部模块调用文件入口时使用的来源系统。 */
    public static final String PLATFORM_SUPPORT = "platform-support";

    /** Client 入驻材料的上传原因，与 t_file_owner_ref.purpose_code 一致。 */
    public static final String CLIENT_ONBOARDING_MATERIAL = "CLIENT_ONBOARDING_MATERIAL";

    private FileSourceCodes() {
    }
}
