package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 两次发布的语言包内容差异。 */
@Getter
@Setter
public class ReleaseDiffResponseBody {

    /** 对比的发布号。 */
    private int releaseNo;

    /** 基准发布号；没有更早的发布时为 null，此时全部为新增。 */
    private Integer baseReleaseNo;

    /** 新增的文案。 */
    private List<ReleaseTextChange> added;

    /** 变化的文案。 */
    private List<ReleaseTextChange> changed;

    /** 删除的文案。 */
    private List<ReleaseTextChange> removed;
}
