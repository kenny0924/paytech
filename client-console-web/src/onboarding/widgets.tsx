import { useContext, useEffect, useMemo } from "react";
import type { ReactNode } from "react";
import type { Flat } from "../onboarding.ts";
import { useLocale, useOptionalT, useT } from "../i18n/react.tsx";
import { Ctx } from "./context.tsx";
import type { CatalogItem, FormSchema, SchemaField } from "./model.ts";
import { REF_COUNTRY_SUBDIVISION, REF_CURRENCY, REFDATA_NAMESPACE, countryOptions, currencyOptions, subdivisionOptions } from "./refdata.ts";
import { OptionsUnavailable, RefSelect } from "./RefSelect.tsx";
import { ADDRESS_COUNTRY, activeAddressFormat, addressPath, addressPathsToClear, draftPath, identifierAlternatives, identifierNumberLabel, identifierPaths, selectedIdentifier } from "./schema.ts";
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


/** 国家名称（Intl → 编码）；供只有编码的页面使用，选项与回退英文名见 refdata.ts。 */
export { countryName } from "./refdata.ts";

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

/**
 * 国家选择：选项只来自 Schema 下发的 Platform ACTIVE 国家（国旗 + 本地化名称，可按本地化名、英文名或编码搜索）
 * <li>国家列表为空表示未能加载：显示重试，不回退到内置列表。</li>
 */
function CountryChoice({ id, path, value, schema, disabled, error, help, title, set, blur }: {
  id: string; path: string; value: string; schema: FormSchema; disabled: boolean; error?: string; help: string | null; title: string;
  set: (path: string, value: string) => void; blur: (path: string) => void;
}) {
  const { locale } = useLocale(), t = useT("onboarding"), retry = useContext(Ctx)?.reloadSchema;
  const options = useMemo(() => countryOptions(schema.countries, locale), [schema.countries, locale]);
  if (!options.length) return <OptionsUnavailable id={id} onRetry={retry ? () => void retry() : undefined} />;
  return <RefSelect id={id} value={value} options={options} disabled={disabled} invalid={!!error} describedBy={described(id, help, error)}
    title={title} placeholder={t("refdata.select_country")} icon="country" hint={t("refdata.search_hint")}
    onChange={(code) => set(path, code)} onBlur={() => blur(path)} />;
}

function Country(props: WidgetProps) {
  const path = draftPath(props.field)!, error = props.errors[path];
  return <FieldShell id={props.id} label={props.label} optional={props.optional} help={props.help} error={error}>
    <CountryChoice id={props.id} path={path} value={props.values[path] ?? ""} schema={props.schema} disabled={props.disabled}
      error={error} help={props.help} title={props.label} set={props.set} blur={props.blur} />
  </FieldShell>;
}

/**
 * 参考数据目录（REF_*）取值：币种显示"本地化名称 (CODE)"与图标；行政区只列给定国家的项
 * <li>目录未下发表示未能加载：显示重试，不回退到内置列表。</li>
 */
function ReferenceChoice({ id, path, value, catalogCode, catalog, countryCode, disabled, error, help, title, set, blur }: {
  id: string; path: string; value: string; catalogCode: string; catalog: CatalogItem[] | undefined; countryCode?: string | null;
  disabled: boolean; error?: string; help: string | null; title: string; set: (path: string, value: string) => void; blur: (path: string) => void;
}) {
  const { locale } = useLocale(), t = useT("onboarding"), refdata = useOptionalT(REFDATA_NAMESPACE), retry = useContext(Ctx)?.reloadSchema;
  const options = useMemo(() => !catalog ? [] : catalogCode === REF_CURRENCY ? currencyOptions(catalog, locale)
    : subdivisionOptions(catalog, countryCode, locale, (key) => refdata(key)), [catalog, catalogCode, countryCode, locale, refdata]);
  if (!catalog) return <OptionsUnavailable id={id} onRetry={retry ? () => void retry() : undefined} />;
  return <RefSelect id={id} value={value} options={options} disabled={disabled} invalid={!!error} describedBy={described(id, help, error)}
    title={title} placeholder={t("form.select")} icon={options.some((option) => option.logo) ? "currency" : "none"}
    hint={catalogCode === REF_CURRENCY ? t("refdata.currency_hint") : undefined}
    onChange={(code) => set(path, code)} onBlur={() => blur(path)} />;
}

/** 目录取值：字段指定 catalogCode；组织形式字段若未指定目录则用 Schema 的组织类型列表。 */
function Select(props: WidgetProps) {
  const t = useT("onboarding"), label = useSchemaText();
  const path = draftPath(props.field)!, error = props.errors[path];
  const catalog = props.field.catalogCode ? props.schema.catalogs[props.field.catalogCode] : undefined;
  if (props.field.catalogCode?.startsWith("REF_")) return <FieldShell id={props.id} label={props.label} optional={props.optional} help={props.help} error={error}>
    <ReferenceChoice id={props.id} path={path} value={props.values[path] ?? ""} catalogCode={props.field.catalogCode} catalog={catalog}
      disabled={props.disabled} error={error} help={props.help} title={props.label} set={props.set} blur={props.blur} />
  </FieldShell>;
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
  const t = useT("onboarding"), label = useSchemaText(), lookup = useSchemaLookup();
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
    <FieldShell id={props.id} label={alternatives.length > 1 ? identifierNumberLabel(typeLabel, (type) => t("identifier.number", { type })) : props.label} optional={props.optional}
      help={props.help} error={error} mono>
      {showMasked ? <div className="ob-masked">
        <span className="mono">{masked}</span>
        {!props.disabled && <button type="button" className="text-button" onClick={() => props.set("~replace.identifier", "1")}>{t("identifier.replace")}</button>}
      </div> : <input id={props.id} name={identifierPaths.value} className="mono" value={value} disabled={props.disabled} autoComplete="off" spellCheck={false}
        maxLength={props.field.validation?.maxLength ?? 64} aria-invalid={!!error} aria-describedby={described(props.id, props.help, error)}
        onChange={(event) => setValue(event.target.value)} onBlur={() => props.blur(identifierPaths.value)} />}
    </FieldShell>
    {type?.issuerScopeMode === "COUNTRY" && <FieldShell id={props.id + "-issuer"} label={t("identifier.issuing_country")} error={props.errors[identifierPaths.issuer]}>
      <CountryChoice id={props.id + "-issuer"} path={identifierPaths.issuer} value={props.values[identifierPaths.issuer] ?? ""} schema={props.schema}
        disabled={props.disabled} error={props.errors[identifierPaths.issuer]} help={null} title={t("identifier.issuing_country")} set={props.set} blur={props.blur} />
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
  const label = useSchemaText(), t = useT("onboarding");
  // 地址国家不是辖区国家时用通用格式；国家改变时切换布局，保留两种格式共有的组件，清空其余。
  const components = activeAddressFormat(props.schema, props.values)?.components ?? [];
  const home = props.countryCode ?? props.schema.countryCode;
  const country = components.find((component) => /^country(code)?$/i.test(component.component));
  const addressCountry = (country ? props.values[addressPath(country)] : "") || home;
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
      const title = label(component.labelKey, component.component);
      if (/^country(code)?$/i.test(component.component)) return <FieldShell key={component.component} id={id} label={title}
        optional={!component.required} error={error} help={null}>
        <CountryChoice id={id} path={path} value={props.values[path] || home || ""} schema={props.schema} disabled={props.disabled} error={error}
          help={null} title={title} set={(_, value) => onAddress(value)} blur={props.blur} />
      </FieldShell>;
      // 行政区随地址国家筛选；该国没有收录行政区时改为文本输入。
      const reference = !!component.catalogCode?.startsWith("REF_");
      const freeText = component.catalogCode === REF_COUNTRY_SUBDIVISION && !!catalog && !catalog.some((item) => item.countryCode === addressCountry);
      if (reference && !freeText)
        return <FieldShell key={component.component} id={id} label={title} optional={!component.required} error={error} help={null}>
          <ReferenceChoice id={id} path={path} value={props.values[path] ?? ""} catalogCode={component.catalogCode!} catalog={catalog}
            countryCode={addressCountry} disabled={props.disabled} error={error} help={null} title={title} set={(_, value) => onAddress(value)} blur={props.blur} />
        </FieldShell>;
      if (catalog && !reference) return <FieldShell key={component.component} id={id} label={title}
        optional={!component.required} error={error} help={null}>
        <Choice id={id} path={path} value={props.values[path] ?? ""} disabled={props.disabled} error={error} help={null}
          options={catalog.map((item) => [item.itemCode, label(item.labelKey, item.itemCode)])}
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
