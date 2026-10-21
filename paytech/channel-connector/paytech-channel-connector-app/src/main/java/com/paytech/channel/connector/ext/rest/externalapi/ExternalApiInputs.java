package com.paytech.channel.connector.ext.rest.externalapi;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;

/**
 * 外部 API 管理入口的最小输入约束（随管理接口自 Platform 迁入，规则与提示原样保留）
 * <li>只做路径编码、文本、分页与包含查询的协议级检查，不承担业务状态规则；不合法时 400 INVALID_REQUEST。</li>
 */
final class ExternalApiInputs {

    /** 工具类不实例化。 */
    private ExternalApiInputs() {
    }

    /** 稳定编码保留大小写，拒绝未知形状。 */
    static String code(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Z][A-Z0-9_]*")) {
            throw new InvalidParameterException("编码必须由大写字母、数字或下划线组成且不超过长度限制");
        }
        return value;
    }

    /** 人类文本去除首尾空白，不允许空说明。 */
    static String text(String value, int max, String field) {
        String result = value == null ? "" : value.strip();
        if (result.isEmpty() || result.length() > max) {
            throw new InvalidParameterException(field + " 不能为空或超长");
        }
        return result;
    }

    /** 公共分页参数，外部 API 管理列表每页最多 100 条。 */
    static PagingRequestBody paging(Integer page, Integer size) {
        var value = PagingRequestBody.of(page, size);
        if (value.size() > 100) {
            throw new InvalidParameterException("每页最多 100 条");
        }
        return value;
    }

    /** 把包含查询中的通配符转成文字，避免用户输入改变筛选语义。 */
    static String contains(String value, int max, String name) {
        if (value == null) {
            return null;
        }
        String text = text(value, max, name);
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
