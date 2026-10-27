import { useEffect, useRef, useState } from "react";
import { flatten } from "../onboarding.ts";
import type { Flat, Values } from "../onboarding.ts";
import type { FormSchema } from "./model.ts";
import { draftPatch, fieldPaths, issueText, localIssues, sendIsolating, serialQueue, serverIssues, withBackoff } from "./draft.ts";
import { addressPath, identifierPaths, isIdentifierLead, isVisible, selectedIdentifier } from "./schema.ts";
import { RequestFailure } from "../auth.ts";
import { translator } from "../i18n/index.ts";

const to = translator("onboarding");
import { useT } from "../i18n/react.tsx";
import type { FieldBlock } from "./schema.ts";
import { useSchemaLookup, useSchemaText } from "./ui.tsx";
import { RenderWidget, wideWidgets } from "./widgets.tsx";

/**
 * 草稿表单状态：本地值、已保存基线、字段错误；失焦后自动保存改动且格式通过的字段
 * <li>保存严格串行：每次保存排在上一次之后执行，执行时才按当时的值与基线计算改动，连续失焦（如浏览器自动填充）合并为一次 PATCH。</li>
 * <li>失焦触发的保存延迟 800ms 合并（同一草稿只有一个 PATCH 在途），被限流时按退避重试；失败不回滚用户的值，基线不前移，下一次保存（含"继续"）会重发并把失败交给调用方。</li>
 */
export function useDraftForm(initial: Values, schema: FormSchema, blocks: FieldBlock[],
  save: (body: { dirtyFields: string[]; values: Values }) => Promise<void>, autosave = true) {
  const [values, setValues] = useState<Flat>(() => flatten(initial));
  const baseline = useRef<Flat>(flatten(initial));
  const current = useRef(values);
  current.current = values;
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false), [failed, setFailed] = useState(false);
  const queue = useRef(serialQueue()), timer = useRef<number | null>(null);
  /** 被服务端拒绝（422）的字段：显示错误并停止重发，直到用户再次修改该字段。 */
  const rejected = useRef(new Set<string>());

  function set(path: string, value: string) {
    rejected.current.delete(path);
    setValues((previous) => ({ ...previous, [path]: value }));
    setErrors((previous) => { if (!previous[path]) return previous; const next = { ...previous }; delete next[path]; return next; });
  }
  function check(path: string) {
    const issue = localIssues(blocks, current.current, schema, false)[path];
    setErrors((previous) => issue ? { ...previous, [path]: issueText(issue) } : previous);
  }
  /** 本表单负责的草稿路径（含证件类型）；其他任务的字段不会混进本页的 PATCH。 */
  function ownedPaths(snapshot: Flat): Set<string> {
    const owned = new Set<string>();
    for (const block of blocks) for (const field of block.fields) {
      for (const item of fieldPaths(field, schema, snapshot)) owned.add(item.path);
      if (field.widget === "IDENTIFIER") owned.add(identifierPaths.type);
      if (field.widget === "ADDRESS") for (const component of [...(schema.addressFormat?.components ?? []), ...(schema.fallbackAddressFormat?.components ?? [])])
        owned.add(addressPath(component));
    }
    return owned;
  }
  async function send(fields: string[], values: Values, snapshot: Flat) {
    const chunk: Values = {};
    for (const key of fields) { const [group, field] = splitPath(key); (chunk[group] ??= {})[field] = values[group][field]; }
    await withBackoff(() => save({ dirtyFields: fields, values: chunk }));
    for (const key of fields) baseline.current[key] = snapshot[key] ?? "";
  }
  /**
   * 执行一次保存：按执行时的值计算本表单字段的改动；返回是否全部保存且无本地格式问题
   * <li>422 时用 sendIsolating 隔离被拒字段：被拒字段显示错误、不再随后续保存重发（直到再次修改），其余字段照常保存。</li>
   */
  async function saveNow(): Promise<boolean> {
    const snapshot = current.current, owned = ownedPaths(snapshot);
    const blocked = new Set([...Object.keys(localIssues(blocks, snapshot, schema, false)), ...rejected.current,
      ...Object.keys(snapshot).filter((key) => !owned.has(key))]);
    const body = draftPatch(baseline.current, snapshot, blocked);
    const formatOk = Object.keys(localIssues(blocks, snapshot, schema, false)).length === 0;
    if (!body.dirtyFields.length) return formatOk && rejected.current.size === 0;
    setSaving(true);
    const refused: Record<string, string> = {};
    try {
      for (let start = 0; start < body.dirtyFields.length; start += 50)
        Object.assign(refused, await sendIsolating(body.dirtyFields.slice(start, start + 50), (fields) => send(fields, body.values, snapshot),
          (failure) => serverIssues(failure, schema), to("error.not_saved_field")));
      if (Object.keys(refused).length) {
        for (const key of Object.keys(refused)) rejected.current.add(key);
        setErrors((previous) => ({ ...previous, ...refused }));
        setFailed(true);
        throw new RequestFailure(422, to("request_error.invalid_draft"), "", refused, "INVALID_DRAFT");
      }
      setFailed(rejected.current.size > 0);
      return formatOk && rejected.current.size === 0;
    } catch (failure) {
      setFailed(true);
      const mapped = serverIssues(failure, schema);
      if (Object.keys(mapped).length) setErrors((previous) => ({ ...previous, ...mapped }));
      throw failure;
    } finally { setSaving(false); }
  }
  /** 排在已有保存之后再保存一次；前一次的失败不阻断本次，本次失败抛给调用方。 */
  function flush(): Promise<boolean> {
    if (timer.current !== null) { clearTimeout(timer.current); timer.current = null; }
    return queue.current(saveNow);
  }
  // 离开页面时若还有未发出的延迟保存，立即排队发出，不丢改动。
  useEffect(() => () => { if (timer.current !== null) { clearTimeout(timer.current); timer.current = null; void queue.current(saveNow).catch(() => {}); } }, []);
  function blur(path: string) {
    check(path);
    if (!autosave) return;
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = window.setTimeout(() => { timer.current = null; void flush().catch(() => {}); }, 800);
  }
  /** 未自动保存的表单（新建人员）取当前改动体。 */
  function pending() { return draftPatch(baseline.current, current.current, new Set()); }
  /** 继续前：本地检查必填与格式，返回问题列表（空 = 可以继续）。 */
  function validate(): { path: string; text: string }[] {
    const issues = localIssues(blocks, current.current, schema, true);
    const texts = Object.fromEntries(Object.entries(issues).map(([path, issue]) => [path, issueText(issue)]));
    setErrors((previous) => ({ ...previous, ...texts }));
    return Object.entries(texts).map(([path, text]) => ({ path, text }));
  }
  /** 服务端返回证件掩码后：清空本地证件原值并接受新的掩码，不让原值留在页面内存里。 */
  function acceptServer(next: Values) {
    const fresh = flatten(next);
    setValues((previous) => {
      const merged = { ...previous };
      for (const [key, value] of Object.entries(fresh)) if (key.endsWith(".identifierMasked")) merged[key] = value;
      for (const key of Object.keys(merged)) if (key.endsWith(".identifierValue") && baseline.current[key] === merged[key] && fresh[key.replace(/Value$/, "Masked")]) {
        merged[key] = ""; baseline.current[key] = ""; delete merged["~replace." + key.split(".")[0]];
      }
      return merged;
    });
  }
  return { values, set, blur, errors, setErrors, saving, failed, flush, validate, acceptServer, pending };
}
const splitPath = (key: string): [string, string] => { const index = key.lastIndexOf("."); return [key.slice(0, index), key.slice(index + 1)]; };

/** 由字段路径找到页面上的输入 id（汇总链接用）。 */
export const inputId = (prefix: string, fieldKey: string) => prefix + "-" + fieldKey.replace(/[^A-Za-z0-9_-]/g, "-");
export function pathToInput(prefix: string, blocks: FieldBlock[], schema: FormSchema, path: string): string {
  for (const block of blocks) for (const field of block.fields) {
    const paths = fieldPaths(field, schema).map((item) => item.path);
    const index = paths.indexOf(path);
    if (index < 0) continue;
    const id = inputId(prefix, field.fieldKey);
    if (field.widget === "ADDRESS") return index === 0 ? id : id + "-" + path.split(".")[1];
    if (path.endsWith(".issuingCountry")) return id + "-issuer";
    if (path.endsWith(".expiryDate")) return id + "-expiry";
    return id;
  }
  return path;
}

/**
 * 按 Schema 渲染分组 → 字段（N3）：每组一个二级标题，宽字段整行，其余两两并排；HIDDEN 与不可见字段不渲染
 */
export function SchemaFields({ prefix, blocks, schema, form, disabled, countryCode, headings = true }: {
  prefix: string; blocks: FieldBlock[]; schema: FormSchema; form: ReturnType<typeof useDraftForm>; disabled: boolean;
  countryCode: string | null; headings?: boolean;
}) {
  const label = useSchemaText(), help = useSchemaLookup();
  let lastGroup = "";
  return <>{blocks.map((block) => {
    const visible = block.fields.filter((field) => isVisible(field, form.values, schema) && isIdentifierLead(field, schema));
    if (!visible.length) return null;
    const heading = headings && block.group.group !== lastGroup;
    lastGroup = block.group.group;
    return <section className="ob-group" key={block.section.section + ":" + block.group.group}>
      {heading && <h2>{label(block.group.labelKey, block.group.group)}</h2>}
      <div className="ob-grid">{visible.map((field) => <div className={wideWidgets.has(field.widget) ? "ob-cell wide" : "ob-cell"} key={field.fieldKey}>
        <RenderWidget field={field} schema={schema} values={form.values} id={inputId(prefix, field.fieldKey)}
          label={label(field.labelKey, field.fieldKey)} help={help(field.helpKey)}
          optional={field.requirementLevel !== "REQUIRED"} errors={form.errors} disabled={disabled}
          set={form.set} blur={form.blur} countryCode={countryCode} />
      </div>)}</div>
    </section>;
  })}</>;
}

/** 汇总项：标签 + 原因；点击聚焦对应输入。 */
export function summaryItems(prefix: string, blocks: FieldBlock[], schema: FormSchema, errors: Record<string, string>,
  labelOf: (path: string) => string, format: (label: string, message: string) => string) {
  return Object.entries(errors).map(([path, text]) => ({ id: pathToInput(prefix, blocks, schema, path), text: format(labelOf(path), text) }));
}


/**
 * 草稿路径 → 汇总里的可读标签
 * <li>地址按组件标签；证件号码用所选证件类型名，签发国 / 到期日用各自标签；找不到时用字段标签，不显示原始路径。</li>
 */
export function useFieldLabel(blocks: FieldBlock[], schema: FormSchema, values: Flat) {
  const label = useSchemaText(), t = useT("onboarding");
  return (path: string): string => {
    for (const block of blocks) for (const field of block.fields) {
      if (!fieldPaths(field, schema, values).some((item) => item.path === path)) continue;
      if (field.widget === "ADDRESS") {
        const component = [...(schema.addressFormat?.components ?? []), ...(schema.fallbackAddressFormat?.components ?? [])]
          .find((item) => addressPath(item) === path);
        return component ? label(component.labelKey, component.component) : label(field.labelKey, field.fieldKey);
      }
      if (field.widget === "IDENTIFIER") {
        if (path === identifierPaths.issuer) return t("identifier.issuing_country");
        if (path === identifierPaths.expiryDate) return t("identifier.expiry_date");
        const type = selectedIdentifier(field, schema, values);
        return type ? label(type.labelKey, type.code) : label(field.labelKey, field.fieldKey);
      }
      return label(field.labelKey, field.fieldKey);
    }
    return label(null, path);
  };
}
