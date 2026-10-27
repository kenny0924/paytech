import { useEffect } from "react";
import type { ReactNode } from "react";
import type { Flat } from "../onboarding.ts";
import { useLocale, useT } from "../i18n/react.tsx";
import type { FormSchema, SchemaField } from "./model.ts";
import { ADDRESS_COUNTRY, activeAddressFormat, addressPath, addressPathsToClear, draftPath, identifierAlternatives, identifierPaths, selectedIdentifier } from "./schema.ts";
import { FieldShell, useSchemaLookup, useSchemaText } from "./ui.tsx";

/**
 * 控件注册表：Schema 的 widget 码 → 渲染函数
 * <li>新控件只在这里登记；国家差异只体现在 Schema 数据（目录、组件、正则），控件不按国家分支。</li>
 * <li>未登记的 widget 按单行文本渲染，不丢字段。</li>
 */
export type WidgetProps = {
  field: SchemaField; schema: FormSchema; values: Flat; id: string; label: string; help: string | null; optional: boolean;
  errors: Record<string, string>; disabled: boolean; set: (path: string, value: string) => void; blur: (path: string) => void;
  countryCode: string | null;
};
type Render = (props: WidgetProps) => ReactNode;


const countryCodes =
  "AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW"
    .split(" ");
const countriesByLocale = new Map<string, string[][]>();
/** 国家 / 地区名称按当前语言显示并排序，每种语言只计算一次。 */
export function countries(locale: string): string[][] {
  let result = countriesByLocale.get(locale);
  if (!result) {
    const names = new Intl.DisplayNames([locale], { type: "region" });
    result = countryCodes.map((code) => [code, names.of(code) ?? code]).sort((a, b) => a[1].localeCompare(b[1], locale));
    countriesByLocale.set(locale, result);
  }
  return result;
}
export function countryName(code: string | null | undefined, locale: string): string {
  if (!code) return "";
  try { return new Intl.DisplayNames([locale], { type: "region" }).of(code) ?? code; } catch { return code; }
}

const described = (id: string, help: string | null, error?: string) =>
  [help ? id + "-help" : "", error ? id + "-error" : ""].filter(Boolean).join(" ") || undefined;

function Text({ type = "text", mono, inputMode, autoComplete }: { type?: string; mono?: boolean; inputMode?: "numeric" | "email" | "tel" | "url" | "text"; autoComplete?: string }): Render {
  return (props) => {
    const path = draftPath(props.field)!, error = props.errors[path];
    return <FieldShell id={props.id} label={props.label} optional={props.optional} help={props.help} error={error} mono={mono}>
      <input id={props.id} name={path} type={type} value={props.values[path] ?? ""} disabled={props.disabled}
        maxLength={props.field.validation?.maxLength ?? undefined} inputMode={inputMode} autoComplete={autoComplete ?? "off"}
        spellCheck={false} className={mono ? "mono" : undefined} aria-invalid={!!error} aria-describedby={described(props.id, props.help, error)}
        onChange={(event) => props.set(path, event.target.value)} onBlur={() => props.blur(path)} />
    </FieldShell>;
  };
}

function TextArea(props: WidgetProps) {
  const path = draftPath(props.field)!, error = props.errors[path];
  return <FieldShell id={props.id} label={props.label} optional={props.optional} help={props.help} error={error} wide>
    <textarea id={props.id} name={path} value={props.values[path] ?? ""} disabled={props.disabled} rows={4}
      maxLength={props.field.validation?.maxLength ?? undefined} aria-invalid={!!error} aria-describedby={described(props.id, props.help, error)}
      onChange={(event) => props.set(path, event.target.value)} onBlur={() => props.blur(path)} />
  </FieldShell>;
}

function Choice({ id, path, value, options, disabled, error, help, set, blur, placeholder }: {
  id: string; path: string; value: string; options: string[][]; disabled: boolean; error?: string; help: string | null;
  set: (path: string, value: string) => void; blur: (path: string) => void; placeholder: string;
}) {
  const known = !value || options.some(([code]) => code === value);
  return <select id={id} name={path} value={value} disabled={disabled} aria-invalid={!!error} aria-describedby={described(id, help, error)}
    onChange={(event) => { set(path, event.target.value); blur(path); }}>
    <option value="">{placeholder}</option>
    {!known && <option value={value} disabled>{value}</option>}
    {options.map(([code, label]) => <option key={code} value={code}>{label}</option>)}
  </select>;
}

function Country(props: WidgetProps) {
  const { locale } = useLocale(), t = useT("onboarding");
  const path = draftPath(props.field)!, error = props.errors[path];
  return <FieldShell id={props.id} label={props.label} optional={props.optional} help={props.help} error={error}>
    <Choice id={props.id} path={path} value={props.values[path] ?? ""} options={countries(locale)} disabled={props.disabled}
      error={error} help={props.help} set={props.set} blur={props.blur} placeholder={t("form.select")} />
  </FieldShell>;
}

/** 目录取值：字段指定 catalogCode；组织形式字段若未指定目录则用 Schema 的组织类型列表。 */
function Select(props: WidgetProps) {
  const t = useT("onboarding"), label = useSchemaText();
  const path = draftPath(props.field)!, error = props.errors[path];
  const catalog = props.field.catalogCode ? props.schema.catalogs[props.field.catalogCode] : undefined;
  const options = catalog?.length
    ? catalog.map((item) => [item.itemCode, label(item.labelKey, item.itemCode)])
    : /organization_?type$/i.test(props.field.fieldKey) ? props.schema.organizationTypes.map((item) => [item.code, label(item.labelKey, item.code)]) : [];
  return <FieldShell id={props.id} label={props.label} optional={props.optional} help={props.help} error={error}>
    <Choice id={props.id} path={path} value={props.values[path] ?? ""} options={options} disabled={props.disabled}
      error={error} help={props.help} set={props.set} blur={props.blur} placeholder={t("form.select")} />
  </FieldShell>;
}

/** 是 / 否单选（如政治公众人物自述、上市公司）；草稿值为 "true" / "false"。 */
function Boolean_(props: WidgetProps) {
  const t = useT("onboarding");
  const path = draftPath(props.field)!, error = props.errors[path], value = props.values[path] ?? "";
  return <fieldset className="ob-field wide" data-field={props.id} aria-describedby={described(props.id, props.help, error)}>
    <legend>{props.label}{props.optional && <span className="ob-optional">{t("form.optional")}</span>}</legend>
    {props.help && <p className="ob-help" id={props.id + "-help"}>{props.help}</p>}
    <div className="ob-radios">{(["false", "true"] as const).map((option) =>
      <label className="checkline" key={option}><input type="radio" id={option === "false" ? props.id : undefined} name={path}
        checked={value.toLowerCase() === option} disabled={props.disabled}
        onChange={() => { props.set(path, option); props.blur(path); }} />{t(option === "true" ? "form.yes" : "form.no")}</label>)}</div>
    {error && <p className="ob-error" id={props.id + "-error"} role="alert">{error}</p>}
  </fieldset>;
}

/** 金额：数字输入（等宽、千分位由浏览器显示），币种由 Schema 或业务资料单独字段提供。 */
const Money = Text({ inputMode: "numeric", mono: true });

/**
 * 证件 / 登记号：等宽；规则来自标识类型目录（正则、校验位、签发国、到期日）
 * <li>个人证件读回只有掩码：显示掩码与"更换"，不回显原值；更换后整号重新提交。</li>
 */
function Identifier(props: WidgetProps) {
  const t = useT("onboarding"), { locale } = useLocale(), label = useSchemaText(), lookup = useSchemaLookup();
  const alternatives = identifierAlternatives(props.field, props.schema);
  const type = selectedIdentifier(props.field, props.schema, props.values);
  const value = props.values[identifierPaths.value] ?? "", masked = props.values[identifierPaths.masked] ?? "";
  const error = props.errors[identifierPaths.value] ?? props.errors[identifierPaths.type];
  const replacing = props.values["~replace.identifier"] === "1";
  const showMasked = masked && !value && !replacing;
  const choose = (code: string) => { props.set(identifierPaths.type, code); props.blur(identifierPaths.type); };
  const setValue = (next: string) => {
    props.set(identifierPaths.value, next);
    if (type && props.values[identifierPaths.type] !== type.code) props.set(identifierPaths.type, type.code);
  };
  const typeLabel = type ? label(type.labelKey, type.code) : props.label;
  return <>
    {alternatives.length > 1 && <fieldset className="ob-field wide" data-field={props.id + "-type"}>
      <legend>{lookup(props.field.requirementGroupLabelKey) ?? t("identifier.type")}</legend>
      <div className="ob-radios">{alternatives.map((item) => { const code = item.fieldKey.split(".")[1];
        return <label className="checkline" key={code}><input type="radio" name={props.id + "-type"} checked={type?.code === code} disabled={props.disabled}
          onChange={() => choose(code)} />{label(item.labelKey, code)}</label>; })}</div>
    </fieldset>}
    <FieldShell id={props.id} label={alternatives.length > 1 ? t("identifier.number", { type: typeLabel }) : props.label} optional={props.optional}
      help={props.help} error={error} mono>
      {showMasked ? <div className="ob-masked">
        <span className="mono">{masked}</span>
        {!props.disabled && <button type="button" className="text-button" onClick={() => props.set("~replace.identifier", "1")}>{t("identifier.replace")}</button>}
      </div> : <input id={props.id} name={identifierPaths.value} className="mono" value={value} disabled={props.disabled} autoComplete="off" spellCheck={false}
        maxLength={props.field.validation?.maxLength ?? 64} aria-invalid={!!error} aria-describedby={described(props.id, props.help, error)}
        onChange={(event) => setValue(event.target.value)} onBlur={() => props.blur(identifierPaths.value)} />}
    </FieldShell>
    {type?.issuerScopeMode === "COUNTRY" && <FieldShell id={props.id + "-issuer"} label={t("identifier.issuing_country")} error={props.errors[identifierPaths.issuer]}>
      <Choice id={props.id + "-issuer"} path={identifierPaths.issuer} value={props.values[identifierPaths.issuer] ?? ""} options={countries(locale)}
        disabled={props.disabled} error={props.errors[identifierPaths.issuer]} help={null} set={props.set} blur={props.blur} placeholder={t("form.select")} />
    </FieldShell>}
    {type?.expiryRequired && <FieldShell id={props.id + "-expiry"} label={t("identifier.expiry_date")} error={props.errors[identifierPaths.expiryDate]}>
      <input id={props.id + "-expiry"} type="date" value={props.values[identifierPaths.expiryDate] ?? ""} disabled={props.disabled}
        aria-invalid={!!props.errors[identifierPaths.expiryDate]}
        onChange={(event) => props.set(identifierPaths.expiryDate, event.target.value)} onBlur={() => props.blur(identifierPaths.expiryDate)} />
    </FieldShell>}
  </>;
}

/** 短组件（号码、邮编）占半行，其余整行；顺序完全来自地址格式组件表。 */
const shortComponent = /(number|pobox|po_box|postalcode|postal_code|zip|makani)$/i;
function Address(props: WidgetProps) {
  const label = useSchemaText(), t = useT("onboarding"), { locale } = useLocale();
  // 地址国家不是辖区国家时用通用格式；国家改变时切换布局，保留两种格式共有的组件，清空其余。
  const components = activeAddressFormat(props.schema, props.values)?.components ?? [];
  const home = props.countryCode ?? props.schema.countryCode;
  const country = components.find((component) => /^country(code)?$/i.test(component.component));
  // 国家组件默认取申请辖区；只在为空时写入一次，用户仍可更改。
  useEffect(() => {
    if (country && home && !props.disabled && !props.values[addressPath(country)]) props.set(addressPath(country), home);
  }, [country?.component, home]);
  return <div className="ob-address wide" data-field={props.id}>
    {components.map((component, index) => {
      const path = addressPath(component), id = index === 0 ? props.id : props.id + "-" + component.component;
      const error = props.errors[path];
      const catalog = component.catalogCode ? props.schema.catalogs[component.catalogCode] : undefined;
      const onAddress = (value: string) => {
        // 草稿只写组件值（addressFormatCode 由 client-master 按国家推导）。
        if (path === ADDRESS_COUNTRY)
          for (const stale of addressPathsToClear(props.schema, props.values, { ...props.values, [path]: value })) props.set(stale, "");
        props.set(path, value);
      };
      if (catalog || /^country(code)?$/i.test(component.component)) return <FieldShell key={component.component} id={id} label={label(component.labelKey, component.component)}
        optional={!component.required} error={error} help={null}>
        <Choice id={id} path={path} value={props.values[path] ?? (catalog ? "" : home ?? "")} disabled={props.disabled} error={error} help={null}
          options={catalog ? catalog.map((item) => [item.itemCode, label(item.labelKey, item.itemCode)]) : countries(locale)}
          set={(_, value) => onAddress(value)} blur={props.blur} placeholder={t("form.select")} />
      </FieldShell>;
      return <FieldShell key={component.component} id={id} label={label(component.labelKey, component.component)} optional={!component.required}
        error={error} help={null} wide={!shortComponent.test(component.component)} mono={/number|pobox|po_box|postal|makani/i.test(component.component)}>
        <input id={id} name={path} value={props.values[path] ?? ""} disabled={props.disabled} autoComplete="off" aria-invalid={!!error}
          className={/number|pobox|po_box|postal|makani/i.test(component.component) ? "mono" : undefined}
          onChange={(event) => onAddress(event.target.value)}
          onBlur={() => props.blur(path)} />
      </FieldShell>;
    })}
  </div>;
}

export const widgets: Record<string, Render> = {
  TEXT: Text({}),
  NAME: Text({ autoComplete: "off" }),
  TEXTAREA: TextArea,
  DATE: Text({ type: "date" }),
  COUNTRY: Country,
  SELECT: Select,
  BOOLEAN: Boolean_,
  IDENTIFIER: Identifier,
  ADDRESS: Address,
  URL: Text({ type: "url", inputMode: "url" }),
  EMAIL: Text({ type: "email", inputMode: "email", autoComplete: "email" }),
  PHONE: Text({ type: "tel", inputMode: "tel", autoComplete: "tel" }),
  MONEY: Money,
};
/** 占整行的控件；其余在宽屏上两两并排。 */
export const wideWidgets = new Set(["TEXT", "NAME", "TEXTAREA", "ADDRESS", "URL", "BOOLEAN"]);
export function RenderWidget(props: WidgetProps) {
  const Widget = widgets[props.field.widget] ?? widgets.TEXT;
  return <Widget {...props} />;
}
