package com.paytech.card.service.processor.response;

import com.paytech.card.core.dal.dataobject.CardBinLibraryVersion;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建 BIN 库版本结果：本次新建，或同一文件已有的原版本 */
@Getter
@AllArgsConstructor
public class CreateBinLibraryVersionResponse {
    /** 版本 */
    private final CardBinLibraryVersion version;

    /** true 为本次新建（201），false 为原版本（200） */
    private final boolean created;
}
