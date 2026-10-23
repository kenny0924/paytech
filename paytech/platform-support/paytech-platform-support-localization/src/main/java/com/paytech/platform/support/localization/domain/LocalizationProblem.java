package com.paytech.platform.support.localization.domain;

/**
 * 一条文案校验问题。
 *
 * @param field 定位路径，如 namespaces.team.team.invite.submit
 * @param message 可外显的中文原因
 */
public record LocalizationProblem(String field, String message) {
}
