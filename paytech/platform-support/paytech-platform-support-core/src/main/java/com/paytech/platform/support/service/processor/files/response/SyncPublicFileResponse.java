package com.paytech.platform.support.service.processor.files.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 公开文件同步的结果。 */
@Getter
@AllArgsConstructor
public class SyncPublicFileResponse {

    /** 替换已结束且同步已完成（含已被更新的替换接管）；为 false 时需稍后重试。 */
    private final boolean finished;

    /** 本次把文件当前版本复制为存储最新版本。 */
    private final boolean restored;
}
