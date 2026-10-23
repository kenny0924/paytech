package com.paytech.platform.support.api.localization;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.localization.request.ActivateReleaseRequestBody;
import com.paytech.platform.support.api.localization.request.PublishReleaseRequestBody;
import com.paytech.platform.support.api.localization.response.CurrentReleaseResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDetailResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDiffResponseBody;
import com.paytech.platform.support.api.localization.response.ReleasePrecheckResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseSummaryResponseBody;

import jakarta.validation.Valid;

/**
 * 多语言发布：预检、发布、历史、详情、差异与切换当前发布；只供 operations-console-bff 私网调用。
 * <li>查询需要 I18N_VIEW，发布与切换需要 I18N_PUBLISH。</li>
 * <li>发布不可变：发布后 key 集合、文案、语言包文件与 Hash 不再改变；检查通过后自动上线，切换不生成新文件。</li>
 */
@HttpExchange(url = "/internal/localization/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface LocalizationReleaseApi {

    /**
     * 发布预检，只读
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @return 覆盖率、是否可发布、阻止发布的问题与发布报告预览
     */
    @GetExchange("/applications/{applicationCode}/release-precheck")
    ReleasePrecheckResponseBody precheckRelease(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode);

    /**
     * 发布当前文案：生成并上传语言包，登记为 PENDING_CHECK；文件检查通过后自动变为 READY，并在应用当前发布号更小时上线
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param request 发布说明
     * @return 新发布摘要，status=PENDING_CHECK；前端轮询详情直到 READY 或 FAILED
     *         <li>必须覆盖语言覆盖率不足返回 422 I18N_COVERAGE_INSUFFICIENT，errors 的 field 为 missing.{语言}，最后一条
     *         field=missingTotal 给出总数；存在 ICU 问题返回 422 VALIDATION_ERROR；没有 ACTIVE key 返回 400。</li>
     *         <li>生成期间文案被修改返回 409 I18N_CONTENT_CHANGED；语言包存储不可用返回 503。</li>
     */
    @PostExchange("/applications/{applicationCode}/releases")
    ReleaseSummaryResponseBody publishRelease(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @Valid @RequestBody PublishReleaseRequestBody request);

    /**
     * 发布历史
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按发布号倒序，含状态与是否当前
     */
    @GetExchange("/applications/{applicationCode}/releases")
    PagingResponseBody<ReleaseSummaryResponseBody> listReleases(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("applicationCode") String applicationCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 发布详情
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param releaseNo 发布号
     * @return 摘要、覆盖率、发布报告、语言包与失败原因
     */
    @GetExchange("/applications/{applicationCode}/releases/{releaseNo}")
    ReleaseDetailResponseBody getRelease(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @PathVariable("releaseNo") Integer releaseNo);

    /**
     * 两次发布的差异：读取两次发布的语言包内容对比
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param releaseNo 对比的发布号
     * @param baseReleaseNo 可选：基准发布号，缺省为小于 releaseNo 的最大发布号
     * @return 按语言与 key 的新增、变化与删除
     *         <li>语言包读取不可用返回 503。</li>
     */
    @GetExchange("/applications/{applicationCode}/releases/{releaseNo}/diff")
    ReleaseDiffResponseBody diffRelease(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @PathVariable("releaseNo") Integer releaseNo,
        @RequestParam(value = "baseReleaseNo", required = false) Integer baseReleaseNo);

    /**
     * 切换应用当前发布（上线或回滚），写审计 I18N_RELEASE_ACTIVATE
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param request 目标发布号与原因
     * @return 切换后的当前发布号；已是当前发布时不写入
     *         <li>目标不是 READY 返回 400。</li>
     */
    @PostExchange("/applications/{applicationCode}/current-release")
    CurrentReleaseResponseBody activateRelease(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @Valid @RequestBody ActivateReleaseRequestBody request);
}
