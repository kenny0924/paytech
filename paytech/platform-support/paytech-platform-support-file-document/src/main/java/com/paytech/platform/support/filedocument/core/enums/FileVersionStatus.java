package com.paytech.platform.support.filedocument.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 文件版本状态（t_file_version.version_status）；Wire 使用 String Code。 */
public enum FileVersionStatus implements CodeEnum {

    /** 检查中；首次上传或替换完成后进入，检查结束前不对外提供。 */
    CANDIDATE("CANDIDATE"),

    /** 当前对外提供的版本；每个文件最多一个。 */
    CURRENT("CURRENT"),

    /** 已被更新版本取代，仍可由运营下载。 */
    SUPERSEDED("SUPERSEDED"),

    /** 检查不通过。 */
    REJECTED("REJECTED");

    private final String code;

    FileVersionStatus(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
