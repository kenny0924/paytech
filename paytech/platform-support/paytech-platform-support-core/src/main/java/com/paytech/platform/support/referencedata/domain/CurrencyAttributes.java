package com.paytech.platform.support.referencedata.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 币种可维护属性：新建与修改写入的目标值
 * <li>数字代码、名称、符号、小数位与舍入方式是标准属性，启用后冻结；logo 文件地址任何状态都可修改。</li>
 */
@Getter
@AllArgsConstructor
public class CurrencyAttributes {

    /** ISO 4217 三位数字代码。 */
    private final String numericCode;

    /** 英文名称，已去除首尾空白。 */
    private final String name;

    /** 货币符号。 */
    private final String symbol;

    /** 最小货币单位位数。 */
    private final int minorUnit;

    /** 舍入方式。 */
    private final CurrencyRoundingMode roundingMode;

    /** logo 文件地址；为空表示没有 logo。 */
    private final String logoFileAddress;

    /** 读取当前行的属性。 */
    public static CurrencyAttributes of(ReferenceCurrency currency) {
        return new CurrencyAttributes(currency.getNumericCode(), currency.getName(), currency.getSymbol(),
            currency.getMinorUnit(), currency.getRoundingMode(), currency.getLogoFileAddress());
    }

    /**
     * 与 current 不同的标准属性
     *
     * @param current 当前属性
     * @return 发生变化的标准属性对应的请求字段名，按请求字段顺序；没有变化为空
     */
    public List<String> changedStandardFields(CurrencyAttributes current) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(numericCode, current.numericCode)) {
            fields.add("numericCode");
        }
        if (!Objects.equals(name, current.name)) {
            fields.add("name");
        }
        if (!Objects.equals(symbol, current.symbol)) {
            fields.add("symbol");
        }
        if (minorUnit != current.minorUnit) {
            fields.add("minorUnit");
        }
        if (roundingMode != current.roundingMode) {
            fields.add("roundingMode");
        }
        return fields;
    }

    /** 标准属性与 logo 都与 current 相同。 */
    public boolean sameAs(CurrencyAttributes current) {
        return changedStandardFields(current).isEmpty() && Objects.equals(logoFileAddress, current.logoFileAddress);
    }
}
