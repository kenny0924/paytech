import {
  createContext,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
} from "react";
import type { FormEvent, ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { RequestFailure } from "./auth.ts";
import {
  changeCatalogField,
  catalogChoices,
  issuingCountryMode,
} from "./catalog.ts";
import type { FormOptions } from "./catalog.ts";
import { describeIssue, readCheck } from "./submission.ts";
import { fallbackText, readWorkspaces, workspaceName, workspaceSubtitle } from "./workspace.ts";
import type { Workspaces } from "./workspace.ts";
import type { SubmissionCheck } from "./submission.ts";
import type { createAuthClient } from "./auth.ts";
import { enumCodes, enumLabel } from "./enum-label.ts";
import { translator } from "./i18n/index.ts";
import { useLocale, useT } from "./i18n/react.tsx";
import { LocaleSwitcher } from "./LocaleSwitcher.tsx";
import {
  base,
  files,
  patch,
  flatten,
  fieldIssues,
  relationPatch,
  createCommand,
  newUpload,
  runUpload,
  readCheckpoint,
  downloadUrl,
  resultBanner,
} from "./onboarding.ts";
import type {
  Application,
  Start,
  Flat,
  Person,
  Relation,
  Requirement,
  Upload,
} from "./onboarding.ts";
type Client = ReturnType<typeof createAuthClient>;
const to = translator("onboarding");
/** 字段：资料路径、控件类型、长度上限；标签在 `onboarding.field.<路径小写下划线>`。 */
type Field = [string, string?, number?];
const individual: Field[] = [
  ["individual.givenName", "text", 64],
  ["individual.middleName", "text", 64],
  ["individual.familyName", "text", 64],
  ["individual.birthDate", "date"],
  ["individual.nationalityCountry", "country"],
  ["individual.residenceCountry", "country"],
];
const organization: Field[] = [
  ["organization.legalName", "text", 64],
  ["organization.tradeName", "text", 64],
  ["organization.incorporationCountry", "country"],
  ["organization.organizationType", "catalog", 64],
  ["organization.incorporationDate", "date"],
];
const address: Field[] = [
  ["address.addressLine1", "text", 255],
  ["address.addressLine2", "text", 255],
  ["address.city", "text", 128],
  ["address.stateOrProvince", "text", 128],
  ["address.postalCode", "text", 32],
  ["address.countryCode", "country"],
];
const identifier: Field[] = [
  ["identifier.identifierType", "catalog", 64],
  ["identifier.identifierValue", "text", 128],
  ["identifier.issuingCountry", "issuer-country"],
  ["identifier.validFrom", "date"],
  ["identifier.validTo", "date"],
];
const business: Field[] = [
  ["business.businessModelCode", "catalog", 64],
  ["business.websiteUrl", "url", 2048],
  ["business.expectedVolume", "text", 25],
  ["business.expectedTicket", "text", 25],
  ["business.currency", "currency"],
  ["contact.email", "email", 320],
  ["contact.phone", "tel", 32],
  ["business.businessDescription", "textarea", 2000],
];
const allFields = [...individual, ...organization, ...address, ...identifier, ...business].map(([key]) => key);
/** 资料路径到标签 key 后缀，如 `individual.givenName` → `field.individual.given_name`。 */
export function fieldLabelKey(key: string): string {
  return "field." + key.replace(/[A-Z]/g, (letter) => "_" + letter.toLowerCase());
}
/** 当前语言下的全部字段标签，供提交核对定位问题。 */
function fieldLabels(): Record<string, string> {
  return Object.fromEntries(allFields.map((key) => [key, to(fieldLabelKey(key))]));
}
const countryCodes =
  "AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW".split(
    " ",
  );
/** 国家 / 地区名称按当前语言显示并排序，每种语言只计算一次。 */
const countriesByLocale = new Map<string, string[][]>();
function countries(locale: string): string[][] {
  let list = countriesByLocale.get(locale);
  if (!list) {
    const regionNames = new Intl.DisplayNames([locale], { type: "region" });
    list = countryCodes
      .map((code) => [code, regionNames.of(code) ?? code])
      .sort((a, b) => a[1].localeCompare(b[1], locale));
    countriesByLocale.set(locale, list);
  }
  return list;
}
const materialName = (type: string) => enumLabel("material", type) ?? type;
/** 审核结果：退回原因与定位分区、通过后开通中或进入工作区、最终拒绝；内容只来自公开说明。 */
function ReviewResultBanner({ app, workspaces, enter, entering }: {
  app: Application; workspaces: Workspaces | null; enter: (memberId: string) => void; entering: boolean;
}) {
  const t = useT("onboarding");
  const banner = resultBanner(app, workspaces);
  if (!banner) return null;
  return (
    <div className={"result-banner " + banner.tone} role={banner.tone === "error" ? "alert" : "status"}>
      <strong>{banner.title}</strong>
      {banner.lines.map((line) => (
        <p key={line}>{line}</p>
      ))}
      {banner.anchor && (
        <a className="secondary" href={"#" + banner.anchor}>
          {t("review.go_fix")}
        </a>
      )}
      {banner.enterMemberId && (
        <button className="secondary" disabled={entering} onClick={() => enter(banner.enterMemberId!)}>
          {entering ? t("workspace.entering") : t("workspace.enter")}
        </button>
      )}
    </div>
  );
}
/** 开始页“我的工作区”：可访问项可进入，受限项只显示公开原因；读取失败不影响入驻申请。 */
function WorkspaceList({ workspaces, entering, enter }: {
  workspaces: Workspaces; entering: string | null; enter: (memberId: string) => void;
}) {
  const t = useT("onboarding");
  return (
    <section className="workspace-list" aria-labelledby="workspace-heading">
      <h2 id="workspace-heading">{t("workspaces.title")}</h2>
      {workspaces.status === "UNAVAILABLE" && <p className="notice">{t("workspaces.unavailable")}</p>}
      {workspaces.status === "AVAILABLE" && workspaces.items.length === 0 && (
        <p className="workspace-empty">{t("workspaces.empty")}</p>
      )}
      {workspaces.items.map((item) => (
        <div className={"workspace-row" + (item.state === "RESTRICTED" ? " restricted" : "")} key={item.memberId}>
          <div>
            <strong>{workspaceName(item)}</strong>
            <small>{[workspaceSubtitle(item), item.current ? t("workspaces.current") : ""].filter(Boolean).join(" · ")}</small>
          </div>
          {item.state === "ACCESSIBLE" ? (
            <button className="secondary" disabled={entering !== null} onClick={() => enter(item.memberId)}>
              {entering === item.memberId ? t("workspace.entering") : t("workspace.enter")}
            </button>
          ) : (
            <span className="workspace-reason">{item.reasonText ?? t("workspaces.restricted")}</span>
          )}
        </div>
      ))}
    </section>
  );
}

function Failure({ error }: { error: Error | null }) {
  const ref = useRef<HTMLDivElement>(null), tc = useT("common");
  useEffect(() => {
    if (
      error &&
      (!document.querySelector("dialog[open]") ||
        ref.current?.closest("dialog"))
    ) {
      ref.current?.focus();
    }
  }, [error]);
  return error ? (
    <div className="notice error" role="alert" tabIndex={-1} ref={ref}>
      {error.message}
      {error instanceof RequestFailure && error.requestId && (
        <small>{tc("request_id", { requestId: error.requestId })}</small>
      )}
    </div>
  ) : null;
}
function SubmissionPanel({
  client,
  app,
  locked,
  dirty,
  subjectName,
  submitted,
}: {
  client: Client;
  app: Application;
  locked: boolean;
  dirty: boolean;
  subjectName: (partyId: string) => string;
  submitted: () => Promise<unknown>;
}) {
  const [check, setCheck] = useState<SubmissionCheck | null>(null),
    [working, setWorking] = useState(false),
    [error, setError] = useState<Error | null>(null);
  const t = useT("onboarding");
  const path = base + "/" + app.applicationId;
  async function runCheck() {
    setWorking(true);
    setError(null);
    try {
      setCheck(readCheck(await client.request<unknown>(path + "/submission-check")));
    } catch (failure) {
      setCheck(null);
      setError(failure instanceof Error ? failure : new Error(to("submit.check_failed")));
    } finally {
      setWorking(false);
    }
  }
  async function submit() {
    if (!window.confirm(to("submit.confirm"))) return;
    setWorking(true);
    setError(null);
    try {
      await client.request(path + "/submissions", { method: "POST" });
      await submitted();
    } catch (failure) {
      setError(failure instanceof Error ? failure : new Error(to("submit.failed")));
      if (failure instanceof RequestFailure && failure.code === "SUBMISSION_INCOMPLETE") {
        setWorking(false);
        await runCheck();
        return;
      }
    } finally {
      setWorking(false);
    }
  }
  const ctx = { app, labels: fieldLabels(), subjectName, materialName };
  return (
    <section id="submission" className="form-section">
      <h2>{t("submit.title")}</h2>
      <p>{t("submit.intro")}</p>
      <Failure error={error} />
      {dirty && <p className="notice">{t("submit.save_first")}</p>}
      {check && check.ready && <p className="notice">{t("submit.ready")}</p>}
      {check && !check.ready && (
        <div className="notice" role="status">
          <strong>{t("submit.issue_count", { count: check.issues.length })}</strong>
          <ul>
            {check.issues.map((issue) => {
              const text = describeIssue(issue, ctx);
              return (
                <li key={issue.field}>
                  {t("submit.issue", { where: text.where, message: text.message })}
                </li>
              );
            })}
          </ul>
        </div>
      )}
      <div className="inline-actions">
        <button className="secondary" disabled={locked || dirty || working} onClick={() => void runCheck()}>
          {working ? t("submit.working") : t("submit.check")}
        </button>
        <button disabled={locked || dirty || working || !check?.ready} onClick={() => void submit()}>
          {t("submit.submit")}
        </button>
      </div>
    </section>
  );
}
function Shell({
  children,
  protect = false,
}: {
  children: ReactNode;
  protect?: boolean;
}) {
  const t = useT("onboarding");
  return (
    <>
      <div
        className="console-bar"
        onClickCapture={(event) => {
          if (
            protect &&
            event.target instanceof Element &&
            event.target.closest("a") &&
            !window.confirm(to("leave.confirm"))
          ) {
            event.preventDefault();
            event.stopPropagation();
          }
        }}
      >
        <Link to="/onboarding" className="console-brand">
          PAYTECH <span>/ {t("shell.console_name")}</span>
        </Link>
        <div className="console-actions">
          <LocaleSwitcher />
          <Link to="/account">{t("shell.account")}</Link>
        </div>
      </div>
      <main className="onboarding">{children}</main>
    </>
  );
}
const FormCatalog = createContext<FormOptions | null>(null);
// 通用目录只按客户类型读取，不随国家变化。
function useFormCatalog(client: Client, type: Application["clientType"]) {
  const key = type;
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{
    key: string;
    data: FormOptions | null;
    error: Error | null;
  } | null>(null);
  useEffect(() => {
    let current = true;
    setState(null);
    client
      .request<FormOptions>(
        "/onboarding/v1/form-options?clientType=" + type,
      )
      .then((data) => {
        if (
          data.clientType !== type ||
          !["AVAILABLE", "UNAVAILABLE"].includes(
            data.catalogStatus,
          ) ||
          !Array.isArray(data.organizationTypes) ||
          !Array.isArray(data.businessModels) ||
          !Array.isArray(data.identifierTypes)
        ) {
          throw new Error(to("catalog.unavailable"));
        }
        if (current) setState({ key, data, error: null });
      })
      .catch((error: Error) => {
        if (current) setState({ key, data: null, error });
      });
    return () => {
      current = false;
    };
  }, [client, type, attempt]);
  return {
    data: state?.key === key ? state.data : null,
    loading: !state || state.key !== key,
    error: state?.key === key ? state.error : null,
    retry: () => setAttempt((n) => n + 1),
  };
}
function CatalogNotice({
  catalog,
}: {
  catalog: ReturnType<typeof useFormCatalog>;
}) {
  const t = useT("onboarding");
  if (catalog.loading)
    return (
      <p className="hint" role="status">
        {t("catalog.loading")}
      </p>
    );
  if (catalog.data?.catalogStatus === "AVAILABLE") return null;
  return (
    <div className="notice" role="status">
      {t("catalog.partial")}
      {catalog.error && <small>{catalog.error.message}</small>}
      <button
        type="button"
        className="secondary compact"
        onClick={catalog.retry}
      >
        {t("catalog.retry")}
      </button>
    </div>
  );
}
function Fields({
  items,
  values,
  set,
  issues = {},
}: {
  items: Field[];
  values: Flat;
  set: (key: string, value: string) => void;
  issues?: Record<string, string>;
}) {
  const prefix = useId();
  const catalog = useContext(FormCatalog);
  const t = useT("onboarding"), { locale } = useLocale();
  // 服务端字段提示：标红控件并以 aria-describedby 关联说明。
  const invalid = (key: string) =>
    issues[key]
      ? { "aria-invalid": true, "aria-describedby": prefix + key + "-issue" }
      : {};
  return (
    <div className="form-grid">
      {items.map(([key, kind = "text", max = 64]) => (
        <div
          className={"field " + (kind === "textarea" ? "wide" : "")}
          key={key}
        >
          <label htmlFor={prefix + key}>{t(fieldLabelKey(key))}</label>
          {kind === "catalog" ? (
            <select
              id={prefix + key}
              {...invalid(key)}
              value={values[key] ?? ""}
              disabled={!catalogChoices(key, values, catalog).length}
              onChange={(event) => set(key, event.target.value)}
            >
              <option value="">
                {catalogChoices(key, values, catalog).length
                  ? t("select.placeholder")
                  : t("select.none")}
              </option>
              {values[key] &&
                !catalogChoices(key, values, catalog).some(
                  (option) => option.code === values[key],
                ) && (
                  <option value={values[key]} disabled>
                    {t("select.legacy", { value: values[key] })}
                  </option>
                )}
              {catalogChoices(key, values, catalog).map((option) => (
                <option key={option.code} value={option.code}>
                  {option.label}
                </option>
              ))}
            </select>
          ) : kind === "issuer-country" &&
            issuingCountryMode(values, catalog) === "NONE" ? (
            <input id={prefix + key} readOnly value={t("field.global_identifier")} />
          ) : kind === "country" ||
            kind === "issuer-country" ||
            kind === "currency" ? (
            <select
              id={prefix + key}
              {...invalid(key)}
              value={values[key] ?? ""}
              onChange={(event) => set(key, event.target.value)}
            >
              <option value="">{t("select.placeholder")}</option>
              {(kind !== "currency"
                ? countries(locale)
                : Intl.supportedValuesOf("currency").map((code) => [code, code])
              ).map(([code, name]) => (
                <option key={code} value={code}>
                  {name}
                </option>
              ))}
            </select>
          ) : kind === "textarea" ? (
            <textarea
              id={prefix + key}
              {...invalid(key)}
              rows={4}
              maxLength={max}
              value={values[key] ?? ""}
              onChange={(event) => set(key, event.target.value)}
            />
          ) : (
            <input
              id={prefix + key}
              {...invalid(key)}
              type={kind}
              maxLength={max}
              value={values[key] ?? ""}
              onChange={(event) => set(key, event.target.value)}
            />
          )}
          {issues[key] && (
            <small className="field-error" id={prefix + key + "-issue"}>
              {issues[key]}
            </small>
          )}
        </div>
      ))}
    </div>
  );
}
function Profile({
  client,
  app,
  busy,
  save,
  onDirty,
}: {
  client: Client;
  app: Application;
  busy: boolean;
  save: (body: unknown) => Promise<Application>;
  onDirty: (dirty: boolean) => void;
}) {
  const [before, setBefore] = useState(() => flatten(app.values)),
    [values, setValues] = useState(before),
    [message, setMessage] = useState(""),
    [issues, setIssues] = useState<Record<string, string>>({});
  const catalog = useFormCatalog(client, app.clientType);
  const t = useT("onboarding");
  useEffect(() => {
    const next = flatten(app.values);
    if (JSON.stringify(next) !== JSON.stringify(before)) {
      setBefore(next);
      setValues(next);
      setMessage(to("profile.synced"));
    }
  }, [app.values]);
  const changes = patch(before, values),
    dirty = changes.dirtyFields.length > 0;
  useEffect(() => {
    onDirty(dirty);
    const handler = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
      }
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty, onDirty]);
  const set = (key: string, value: string) => {
    setMessage("");
    setIssues(({ [key]: _, ...rest }) => rest);
    setValues((old) => changeCatalogField(old, key, value, catalog.data));
  };
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!dirty) return;
    setIssues({});
    try {
      const updated = await save(changes);
      const next = flatten(updated.values);
      setBefore(next);
      setValues(next);
      setMessage(to("profile.saved"));
    } catch (failure) {
      /* 页面统一显示可重试错误；字段级问题同时标在对应表单项。 */
      setIssues(fieldIssues(failure, allFields));
    }
  }
  return (
    <FormCatalog.Provider value={catalog.data}>
      <form onSubmit={submit}>
        <CatalogNotice catalog={catalog} />
        <fieldset disabled={!app.editable || busy}>
          <section id="profile" className="form-section">
            <h2>
              {app.clientType === "ORGANIZATION" ? t("profile.organization_title") : t("profile.individual_title")}
            </h2>
            <Fields
              items={
                app.clientType === "ORGANIZATION" ? organization : individual
              }
              values={values}
              set={set}
              issues={issues}
            />
            <h3>
              {app.clientType === "ORGANIZATION" ? t("profile.registered_address") : t("profile.residential_address")}
            </h3>
            <Fields items={address} values={values} set={set} issues={issues} />
            <h3>{t("profile.identification")}</h3>
            <Fields items={identifier} values={values} set={set} issues={issues} />
          </section>
          <section id="business" className="form-section">
            <h2>{t("section.business")}</h2>
            <p>{t("profile.business_intro")}</p>
            <Fields items={business} values={values} set={set} issues={issues} />
          </section>
        </fieldset>
        {app.editable && (
          <div className="form-actions">
            <span role="status">
              {message || (dirty ? t("profile.unsaved") : t("profile.synced"))}
            </span>
            <button className="primary compact" disabled={busy || !dirty}>
              {t("profile.save")}
            </button>
          </div>
        )}
      </form>
    </FormCatalog.Provider>
  );
}
const CommandFeedback = createContext<{
  error: Error | null;
  pending: boolean;
  busy: boolean;
  retry: () => void;
}>({ error: null, pending: false, busy: false, retry: () => {} });
function Modal({
  title,
  children,
  close,
}: {
  title: string;
  children: ReactNode;
  close: () => void;
}) {
  const feedback = useContext(CommandFeedback);
  const ref = useRef<HTMLDialogElement>(null), t = useT("onboarding");
  useEffect(() => {
    const node = ref.current;
    node?.showModal();
    return () => node?.close();
  }, []);
  return (
    <dialog
      className="editor"
      ref={ref}
      aria-labelledby="editor-title"
      onCancel={(event) => {
        event.preventDefault();
        close();
      }}
    >
      <div className="editor-title">
        <h2 id="editor-title">{title}</h2>
        <button
          type="button"
          className="secondary"
          onClick={close}
          aria-label={t("modal.close_label")}
        >
          {t("modal.close")}
        </button>
      </div>
      {children}
      <Failure error={feedback.error} />
      {feedback.pending && (
        <button
          className="secondary full"
          disabled={feedback.busy}
          onClick={feedback.retry}
        >
          {t("command.retry")}
        </button>
      )}
    </dialog>
  );
}
function RelationFields({
  values,
  set,
}: {
  values: Flat;
  set: (key: string, value: string) => void;
}) {
  const t = useT("onboarding");
  return (
    <>
      <div className="field">
        <label htmlFor="relationType">{t("relation.type")}</label>
        <select
          id="relationType"
          value={values.relationType ?? ""}
          required
          onChange={(e) => {
            set("relationType", e.target.value);
            set("ownershipPercentage", "");
          }}
        >
          <option value="">{t("select.placeholder")}</option>
          {enumCodes.relation_type.map((key) => (
            <option key={key} value={key}>
              {enumLabel("relation_type", key)}
            </option>
          ))}
        </select>
      </div>
      <div className="form-grid">
        {["OWNERSHIP", "UBO"].includes(values.relationType) && (
          <div className="field">
            <label htmlFor="ownershipPercentage">{t("relation.ownership")}</label>
            <input
              id="ownershipPercentage"
              type="text"
              inputMode="decimal"
              value={values.ownershipPercentage ?? ""}
              onChange={(e) => set("ownershipPercentage", e.target.value)}
            />
          </div>
        )}
        <div className="field">
          <label htmlFor="effectiveFrom">{t("relation.effective_from")}</label>
          <input
            id="effectiveFrom"
            type="date"
            required
            value={values.effectiveFrom ?? ""}
            onChange={(e) => set("effectiveFrom", e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="effectiveTo">{t("relation.effective_to")}</label>
          <input
            id="effectiveTo"
            type="date"
            value={values.effectiveTo ?? ""}
            onChange={(e) => set("effectiveTo", e.target.value)}
          />
        </div>
      </div>
    </>
  );
}
const relationBody = (values: Flat) => ({
  relationType: values.relationType,
  ownershipPercentage: values.ownershipPercentage || null,
  effectiveFrom: values.effectiveFrom,
  effectiveTo: values.effectiveTo || null,
});
function PersonEditor({
  client,
  person,
  busy,
  save,
  close,
}: {
  client: Client;
  person?: Person;
  busy: boolean;
  save: (body: unknown) => Promise<unknown>;
  close: () => void;
}) {
  const before = flatten(person?.values ?? {}),
    [values, setValues] = useState(before),
    [relation, setRelation] = useState<Flat>({
      relationType: "",
      effectiveFrom: "",
    });
  const catalog = useFormCatalog(client, "INDIVIDUAL");
  const t = useT("onboarding");
  const dirty = patch(before, values).dirtyFields.length > 0;
  const exit = () => {
    if (!busy && (!dirty || window.confirm(to("person.discard_confirm"))))
      close();
  };
  async function submit(event: FormEvent) {
    event.preventDefault();
    try {
      await save(
        person
          ? patch(before, values)
          : { person: patch({}, values), relation: relationBody(relation) },
      );
      close();
    } catch {
      /* 父页保留原命令供重试。 */
    }
  }
  return (
    <Modal title={person ? t("person.edit") : t("person.add")} close={exit}>
      <FormCatalog.Provider value={catalog.data}>
        <CatalogNotice catalog={catalog} />
        <form onSubmit={submit}>
          <fieldset disabled={busy || person?.editable === false}>
            <Fields
              items={individual}
              values={values}
              set={(key, value) =>
                setValues((old) =>
                  changeCatalogField(old, key, value, catalog.data),
                )
              }
            />
            <h3>{t("profile.residential_address")}</h3>
            <Fields
              items={address}
              values={values}
              set={(key, value) =>
                setValues((old) =>
                  changeCatalogField(old, key, value, catalog.data),
                )
              }
            />
            <h3>{t("profile.identification")}</h3>
            <Fields
              items={identifier}
              values={values}
              set={(key, value) =>
                setValues((old) =>
                  changeCatalogField(old, key, value, catalog.data),
                )
              }
            />
            {!person && (
              <>
                <h3>{t("person.relation_title")}</h3>
                <RelationFields
                  values={relation}
                  set={(key, value) =>
                    setRelation((old) => ({ ...old, [key]: value }))
                  }
                />
              </>
            )}
          </fieldset>
          <button
            className="primary"
            disabled={busy || !dirty || person?.editable === false}
          >
            {t("person.save")}
          </button>
        </form>
      </FormCatalog.Provider>
    </Modal>
  );
}
function RelationEditor({
  relation,
  party,
  busy,
  save,
  close,
}: {
  relation?: Relation;
  party: string;
  busy: boolean;
  save: (body: unknown) => Promise<unknown>;
  close: () => void;
}) {
  const initial: Flat = {
    relationType: relation?.relationType ?? "",
    ownershipPercentage: relation?.ownershipPercentage ?? "",
    effectiveFrom: relation?.effectiveFrom?.slice(0, 10) ?? "",
    effectiveTo: relation?.effectiveTo?.slice(0, 10) ?? "",
  };
  const [values, setValues] = useState(initial);
  const t = useT("onboarding");
  const changed = relationPatch(relationBody(initial), relationBody(values));
  async function submit(event: FormEvent) {
    event.preventDefault();
    const next = relationBody(values);
    if (relation && !changed.dirtyFields.length) {
      return;
    }
    try {
      await save(relation ? changed : { relatedPartyId: party, ...next });
      close();
    } catch {
      /* 保留输入供原命令重试。 */
    }
  }
  return (
    <Modal
      title={relation ? t("relation.edit") : t("relation.add")}
      close={() => {
        if (!busy) close();
      }}
    >
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <RelationFields
            values={values}
            set={(key, value) => setValues((old) => ({ ...old, [key]: value }))}
          />
        </fieldset>
        <button
          className="primary"
          disabled={busy || (!!relation && !changed.dirtyFields.length)}
        >
          {t("relation.save")}
        </button>
      </form>
    </Modal>
  );
}
function UploadEditor({
  app,
  subject,
  requirement,
  client,
  resume,
  done,
  close,
}: {
  app: Application;
  subject: string;
  requirement: Requirement;
  client: Client;
  resume?: Upload;
  done: () => Promise<void>;
  close: () => void;
}) {
  const account = client.current()?.context.accountId ?? "";
  const session = client.current()?.context.sessionId;
  const storageKey = "client-upload:" + account + ":" + app.applicationId;
  const [state] = useState(
    () =>
      resume ??
      newUpload(
        app.applicationId,
        subject,
        requirement.requirementCode,
        requirement.documentType,
      ),
  );
  const [file, setFile] = useState<File>();
  const [busy, setBusy] = useState(false),
    [error, setError] = useState<Error | null>(null),
    [phase, setPhase] = useState(state.phase);
  const guard = useRef(false), t = useT("onboarding"), tc = useT("common");
  const checkpoint = (value: Upload) => {
    setPhase(value.phase);
    try {
      if (value.phase === "DONE") sessionStorage.removeItem(storageKey);
      else sessionStorage.setItem(storageKey, JSON.stringify(value));
    } catch {
      /* 存储不可用时仍可在本页重试。 */
    }
  };
  async function advance() {
    if (guard.current) return;
    guard.current = true;
    setBusy(true);
    setError(null);
    try {
      await runUpload(
        state,
        async <T,>(path: string, options?: import("./auth.ts").ApiOptions) => {
          if (
            client.current()?.context.accountId !== account ||
            client.current()?.context.sessionId !== session
          )
            throw new RequestFailure(401, to("upload.session_changed"));
          return client.request<T>(path, options);
        },
        file,
        fetch,
        checkpoint,
      );
      if (state.phase === "DONE") {
        await done();
        close();
      }
    } catch (failure) {
      setError(failure as Error);
    } finally {
      setPhase(state.phase);
      setBusy(false);
      guard.current = false;
    }
  }
  return (
    <Modal
      title={t("upload.title")}
      close={() => {
        if (!busy) close();
      }}
    >
      <p>
        {materialName(requirement.documentType)}
      </p>
      <p className="notice">
        {t("upload.scan_notice")}
      </p>
      {phase === "PUT" && (
        <div className="field">
          <label htmlFor="material-file">{t("upload.choose")}</label>
          <input
            id="material-file"
            type="file"
            disabled={busy || !!file}
            onChange={(e) => setFile(e.target.files?.[0])}
          />
        </div>
      )}
      <p role="status">
        {busy
          ? tc("status.processing")
          : phase === "SCAN"
            ? t("upload.phase.scan")
            : phase === "COMPLETE"
              ? t("upload.phase.complete")
              : phase === "ASSOCIATE"
                ? t("upload.phase.associate")
                : phase === "DONE"
                  ? t("upload.phase.done")
                  : file
                    ? t("upload.selected")
                    : t("upload.select_prompt")}
      </p>
      <Failure error={error} />
      <button
        className="primary"
        onClick={advance}
        disabled={busy || (phase === "PUT" && !file)}
      >
        {phase === "SCAN"
          ? t("upload.check_scan")
          : error
            ? t("command.retry")
            : t("upload.continue")}
      </button>
      {!busy && (
        <button
          className="secondary full"
          onClick={() => {
            if (
              window.confirm(to("upload.discard_confirm"))
            ) {
              sessionStorage.removeItem(storageKey);
              close();
            }
          }}
        >
          {t("upload.discard")}
        </button>
      )}
    </Modal>
  );
}
export function OnboardingPage({ client }: { client: Client }) {
  const { applicationId } = useParams(),
    navigate = useNavigate();
  const [start, setStart] = useState<Start | null>(null),
    [app, setApp] = useState<Application | null>(null),
    [error, setError] = useState<Error | null>(null),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [dirty, setDirty] = useState(false),
    [pending, setPending] = useState(false),
    [workspaces, setWorkspaces] = useState<Workspaces | null>(null),
    [entering, setEntering] = useState<string | null>(null),
    [notice] = useState(() => fallbackText(client.takeFallback()));
  const command = useRef<ReturnType<typeof createCommand> | null>(null),
    guard = useRef(false);
  const t = useT("onboarding"), tc = useT("common");
  const [editor, setEditor] = useState<{
      kind: "person" | "relation";
      party?: string;
      relation?: Relation;
    } | null>(null),
    [upload, setUpload] = useState<{
      subject: string;
      requirement: Requirement;
      resume?: Upload;
    } | null>(null);
  const validId =
    applicationId &&
    /^[1-9][0-9]{0,18}$/.test(applicationId) &&
    BigInt(applicationId) <= 9223372036854775807n;
  async function reload() {
    if (!validId) throw Error(to("page.not_found"));
    const value = await client.request<Application>(base + "/" + applicationId);
    setApp(value);
    return value;
  }
  function fail(failure: unknown) {
    const value = failure instanceof Error ? failure : new Error(to("page.action_failed"));
    if (value instanceof RequestFailure && value.status === 401) {
      client.clear();
      navigate("/login", { replace: true });
    } else setError(value);
  }
  useEffect(() => {
    let alive = true;
    setLoading(true);
    setApp(null);
    setStart(null);
    setError(null);
    setEditor(null);
    setUpload(null);
    command.current = null;
    setPending(false);
    setDirty(false);
    const request = applicationId
      ? validId
        ? client.request<Application>(base + "/" + applicationId)
        : Promise.reject(Error(to("page.not_found")))
      : client.request<Start>("/onboarding/v1/start");
    request
      .then((value) => {
        if (alive) {
          if (applicationId) setApp(value as Application);
          else {
            setStart(value as Start);
            setWorkspaces(readWorkspaces((value as Start).workspaces));
          }
        }
      })
      .catch((failure) => {
        if (alive) fail(failure);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [applicationId]);
  // 审核通过的申请页需要工作区状态，才能判断显示“进入工作区”还是“正在开通”。
  useEffect(() => {
    if (!app || app.status !== "APPROVED") return;
    let alive = true;
    client.request<Start>("/onboarding/v1/start").then((value) => {
      if (alive) setWorkspaces(readWorkspaces(value.workspaces));
    }).catch(() => {
      if (alive) setWorkspaces(null);
    });
    return () => {
      alive = false;
    };
  }, [app?.applicationId, app?.status]);
  /** 进入工作区；失败留在当前页并重新读取工作区状态（不能强行进入）。 */
  async function enterWorkspace(memberId: string) {
    if (entering) return;
    setEntering(memberId);
    setError(null);
    try {
      await client.selectWorkspace(memberId);
      navigate("/workspace");
    } catch (failure) {
      fail(failure);
      try {
        setWorkspaces(readWorkspaces((await client.request<Start>("/onboarding/v1/start")).workspaces));
      } catch {
        setWorkspaces({ status: "UNAVAILABLE", items: [] });
      }
    } finally {
      setEntering(null);
    }
  }
  const locked = busy || pending;
  async function execute(
    path: string,
    method: "POST" | "PATCH" | "DELETE",
    body?: unknown,
  ): Promise<Application> {
    if (guard.current) throw Error(to("page.wait"));
    guard.current = true;
    setBusy(true);
    setError(null);
    if (!command.current)
      command.current = createCommand(client.request, path, method, body);
    setPending(true);
    try {
      const receipt = (await command.current.run()) as {
        applicationId?: string;
      };
      if (!applicationId) {
        command.current = null;
        setPending(false);
        navigate("/onboarding/applications/" + receipt.applicationId);
        return {} as Application;
      }
      const updated = await reload();
      command.current = null;
      setPending(false);
      return updated;
    } catch (failure) {
      if (
        failure instanceof RequestFailure &&
        ([400, 403, 404, 422].includes(failure.status) ||
          (failure.status === 409 &&
            [
              "APPLICATION_NOT_EDITABLE",
              "PERSON_NOT_EDITABLE",
              "RELATION_NOT_EDITABLE",
              "RELATION_ALREADY_EXISTS",
              "ACTIVE_APPLICATION_TYPE_MISMATCH",
              "IDEMPOTENCY_CONFLICT",
              "DOCUMENT_ALREADY_EXISTS",
              "MATERIAL_FILE_UNAVAILABLE",
              "MATERIAL_CONTEXT_CHANGED",
            ].includes(failure.code)))
      ) {
        command.current = null;
        setPending(false);
      }
      fail(failure);
      throw failure;
    } finally {
      guard.current = false;
      setBusy(false);
    }
  }
  async function retry() {
    try {
      await execute("", "POST");
      setEditor(null);
    } catch {
      /* 错误已显示。 */
    }
  }
  function resumeUpload() {
    const key =
      "client-upload:" +
      (client.current()?.context.accountId ?? "") +
      ":" +
      applicationId;
    const state = readCheckpoint(
      (() => {
        try {
          return sessionStorage.getItem(key);
        } catch {
          return null;
        }
      })(),
      applicationId!,
    );
    if (state)
      setUpload({
        subject: state.subjectPartyId,
        requirement: {
          requirementCode: state.requirementCode,
          documentType: state.documentType,
          requiredFlag: false,
        },
        resume: state,
      });
    else setError(new Error(to("upload.nothing_to_resume")));
  }
  function beginUpload(subject: string, requirement: Requirement) {
    const storageKey =
      "client-upload:" +
      (client.current()?.context.accountId ?? "") +
      ":" +
      applicationId;
    let existing: string | null = null;
    try {
      existing = sessionStorage.getItem(storageKey);
    } catch {
      /* 内存模式仍可上传。 */
    }
    if (
      existing &&
      !window.confirm(to("upload.replace_confirm"))
    )
      return;
    setUpload({ subject, requirement });
  }
  async function download(fileId: string) {
    setError(null);
    try {
      const grant = await client.request<{ url: string; expiresAt: string }>(
        files + "/files/" + fileId + "/download",
        { method: "POST" },
      );
      const link = document.createElement("a");
      link.href = downloadUrl(grant);
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.referrerPolicy = "no-referrer";
      link.click();
    } catch (failure) {
      fail(failure);
    }
  }
  const personName = (person: Person) =>
    [person.values.individual?.givenName, person.values.individual?.familyName]
      .filter(Boolean)
      .join(" ") || to("person.name_missing");
  return (
    <CommandFeedback.Provider
      value={{ error, pending, busy, retry: () => void retry() }}
    >
      <Shell protect={dirty || pending}>
        <div className="page-heading">
          <div>
            <h1>
              {app
                ? app.clientType === "ORGANIZATION"
                  ? t("page.title_organization")
                  : t("page.title_individual")
                : t("page.title_start")}
            </h1>
            <p>
              {app
                ? t("page.intro_application")
                : t("page.intro_start")}
            </p>
          </div>
          {app && (
            <span className="status">
              {enumLabel("application_status", app.status) ?? t("page.status_unconfirmed")}
            </span>
          )}
        </div>
        {app && (
          <nav className="section-nav">
            <a href="#profile">{t("section.basic")}</a>
            <a href="#business">{t("section.business")}</a>
            {app.clientType === "ORGANIZATION" && (
              <a href="#people">{t("section.people")}</a>
            )}
            <a href="#materials">{t("section.materials")}</a>
            <Link
              to="/onboarding"
              onClick={(event) => {
                if (
                  (dirty || pending) &&
                  !window.confirm(to("leave.confirm"))
                )
                  event.preventDefault();
              }}
            >
              {t("page.back_to_list")}
            </Link>
          </nav>
        )}
        <Failure error={error} />
        {pending && (
          <div className="notice" role="status">
            {t("command.unconfirmed")}
            <button className="secondary" disabled={busy} onClick={retry}>
              {t("command.retry")}
            </button>
          </div>
        )}
        {loading && <p role="status">{tc("status.loading")}</p>}
        {!loading && error && !app && !start && (
          <button
            className="secondary"
            onClick={() => window.location.reload()}
          >
            {t("page.reload")}
          </button>
        )}
        {notice && !app && <p className="notice" role="status">{notice}</p>}
        {start && (
          <>
            {workspaces && <WorkspaceList workspaces={workspaces} entering={entering} enter={(id) => void enterWorkspace(id)} />}
            <h2 className="section-title">{t("start.applications_title")}</h2>
            <div className="application-list">
              {start.applications.map((row) => (
                <Link
                  className="application-row"
                  key={row.applicationId}
                  to={"/onboarding/applications/" + row.applicationId}
                >
                  <div>
                    <strong>
                      {row.clientType === "ORGANIZATION" ? t("start.row_organization") : t("start.row_individual")}
                    </strong>
                    <small>{row.applicationNo}</small>
                  </div>
                  <span>{t("start.row_status", { status: enumLabel("application_status", row.status) ?? t("start.view") })}</span>
                </Link>
              ))}
            </div>
            {start.hasMore && <p>{t("start.has_more")}</p>}
            {!start.activeApplicationId && (
              <div className="type-choices">
                {(["INDIVIDUAL", "ORGANIZATION"] as const).map((type) => (
                  <button
                    key={type}
                    className="type-choice"
                    disabled={locked}
                    onClick={() =>
                      void execute(base, "POST", { clientType: type }).catch(
                        () => {},
                      )
                    }
                  >
                    <strong>
                      {type === "INDIVIDUAL"
                        ? t("start.individual_title")
                        : t("start.organization_title")}
                    </strong>
                    <span>
                      {type === "INDIVIDUAL"
                        ? t("start.individual_desc")
                        : t("start.organization_desc")}
                    </span>
                    <b>{t("start.begin")}</b>
                  </button>
                ))}
              </div>
            )}
          </>
        )}
        {app && (
          <>
            <ReviewResultBanner app={app} workspaces={workspaces} enter={(id) => void enterWorkspace(id)} entering={entering !== null} />
            {!app.editable && !resultBanner(app) && (
              <p className="notice">
                {["SUBMITTED", "UNDER_REVIEW"].includes(app.status)
                  ? t("page.under_review")
                  : t("page.read_only")}
              </p>
            )}
            <Profile
              client={client}
              key={app.applicationId}
              app={app}
              busy={locked}
              onDirty={setDirty}
              save={(body) =>
                execute(base + "/" + applicationId + "/draft", "PATCH", body)
              }
            />
            {app.clientType === "ORGANIZATION" && (
              <section id="people" className="form-section">
                <h2>{t("section.people")}</h2>
                <p>{t("people.intro")}</p>
                {dirty && (
                  <p className="notice">{t("people.save_first")}</p>
                )}
                {app.people.map((person) => (
                  <div className="person-row" key={person.partyId}>
                    <div className="row-heading">
                      <strong>{personName(person)}</strong>
                      <div className="inline-actions">
                        <button
                          className="text-button"
                          disabled={locked || dirty}
                          onClick={() =>
                            setEditor({ kind: "person", party: person.partyId })
                          }
                        >
                          {person.editable && app.editable
                            ? t("person.edit")
                            : t("person.view")}
                        </button>
                        {app.editable && (
                          <button
                            className="text-button"
                            disabled={locked || dirty}
                            onClick={() =>
                              setEditor({
                                kind: "relation",
                                party: person.partyId,
                              })
                            }
                          >
                            {t("relation.add")}
                          </button>
                        )}
                      </div>
                    </div>
                    {app.relations
                      .filter((r) => r.relatedPartyId === person.partyId)
                      .map((relation) => (
                        <div className="relation-row" key={relation.relationId}>
                          <span>
                            {[
                              enumLabel("relation_type", relation.relationType) ?? relation.relationType,
                              relation.ownershipPercentage
                                ? t("relation.ownership_value", { value: relation.ownershipPercentage })
                                : null,
                              relation.status === "REMOVED" ? t("relation.removed") : null,
                            ].filter(Boolean).join(" · ")}
                          </span>
                          {app.editable && relation.editable && (
                            <div className="inline-actions">
                              <button
                                className="text-button"
                                disabled={locked || dirty}
                                onClick={() =>
                                  setEditor({
                                    kind: "relation",
                                    party: person.partyId,
                                    relation,
                                  })
                                }
                              >
                                {t("relation.edit")}
                              </button>
                              <button
                                className="text-button"
                                disabled={locked || dirty}
                                onClick={() => {
                                  if (
                                    window.confirm(to("relation.remove_confirm"))
                                  )
                                    void execute(
                                      base +
                                        "/" +
                                        applicationId +
                                        "/relations/" +
                                        relation.relationId,
                                      "DELETE",
                                    ).catch(() => {});
                                }}
                              >
                                {t("action.remove")}
                              </button>
                            </div>
                          )}
                        </div>
                      ))}
                  </div>
                ))}
                {app.editable && (
                  <button
                    className="secondary"
                    disabled={locked || dirty}
                    onClick={() => setEditor({ kind: "person" })}
                  >
                    {t("people.add")}
                  </button>
                )}
              </section>
            )}
            <section id="materials" className="form-section">
              <div className="row-heading">
                <h2>{t("section.materials")}</h2>
                {app.editable && (
                  <button
                    className="text-button"
                    disabled={locked || dirty}
                    onClick={resumeUpload}
                  >
                    {t("materials.resume")}
                  </button>
                )}
              </div>
              <p>
                {t("materials.intro")}
              </p>
              {app.materialSubjects.map((subject) => (
                <div className="material-subject" key={subject.subjectPartyId}>
                  <h3>
                    {subject.subjectPartyId === app.subjectPartyId
                      ? t("materials.subject")
                      : personName(
                          app.people.find(
                            (p) => p.partyId === subject.subjectPartyId,
                          )!,
                        )}
                  </h3>
                  {subject.requirementsStatus !== "AVAILABLE" ? (
                    <p className="notice">
                      {t("materials.requirements_unavailable")}
                    </p>
                  ) : (
                    subject.requirements.map((requirement) => (
                      <div
                        className="material-row"
                        key={requirement.requirementCode}
                      >
                        <span>
                          {materialName(requirement.documentType)}
                          <small>
                            {requirement.requiredFlag ? t("materials.required") : t("materials.optional")}
                          </small>
                        </span>
                        {app.editable && (
                          <button
                            className="secondary"
                            disabled={locked || dirty}
                            onClick={() =>
                              beginUpload(subject.subjectPartyId, requirement)
                            }
                          >
                            {t("materials.upload")}
                          </button>
                        )}
                      </div>
                    ))
                  )}
                </div>
              ))}
              {app.documents.map((doc) => (
                <div className="document-row" key={doc.documentId}>
                  <div>
                    <strong>{doc.file?.displayName ?? t("materials.file_fallback")}</strong>
                    <small>
                      {[
                        !doc.subjectInScope ? t("materials.out_of_scope") : null,
                        doc.fileStatus !== "AVAILABLE"
                          ? t("materials.file_status_unavailable")
                          : doc.file?.objectStatus !== "ACTIVE"
                            ? t("materials.file_unavailable")
                            : (enumLabel("scan_status", doc.file?.scanStatus) ?? t("page.status_unconfirmed")),
                      ].filter(Boolean).join(" · ")}
                    </small>
                  </div>
                  <div className="inline-actions">
                    {doc.file?.scanStatus === "AVAILABLE" &&
                      doc.file.objectStatus === "ACTIVE" &&
                      doc.file.allowedActions.includes("DOWNLOAD") && (
                        <button
                          className="text-button"
                          onClick={() => void download(doc.fileId)}
                        >
                          {t("materials.view")}
                        </button>
                      )}
                    {app.editable && (
                      <button
                        className="text-button"
                        disabled={locked || dirty}
                        onClick={() => {
                          if (window.confirm(to("materials.remove_confirm")))
                            void execute(
                              base +
                                "/" +
                                applicationId +
                                "/documents/" +
                                doc.documentId,
                              "DELETE",
                            ).catch(() => {});
                        }}
                      >
                        {t("action.remove")}
                      </button>
                    )}
                  </div>
                </div>
              ))}
              <button
                className="secondary"
                disabled={locked || dirty}
                onClick={() => void reload().catch(fail)}
              >
                {t("materials.reload")}
              </button>
            </section>
            {app.editable && (
              <SubmissionPanel
                client={client}
                key={app.applicationId + ":" + app.savedAt}
                app={app}
                locked={locked}
                dirty={dirty}
                subjectName={(partyId) => {
                  if (partyId === app.subjectPartyId) return to("submit.subject");
                  const person = app.people.find((p) => p.partyId === partyId);
                  return person ? personName(person) : to("submit.person");
                }}
                submitted={reload}
              />
            )}
          </>
        )}
        {app && editor?.kind === "person" && (
          <PersonEditor
            client={client}
            key={editor.party ?? "new"}
            person={(() => {
              const person = app.people.find((p) => p.partyId === editor.party);
              return person
                ? { ...person, editable: app.editable && person.editable }
                : undefined;
            })()}
            busy={locked}
            close={() => setEditor(null)}
            save={(body) =>
              execute(
                base +
                  "/" +
                  applicationId +
                  "/people" +
                  (editor.party ? "/" + editor.party : ""),
                editor.party ? "PATCH" : "POST",
                body,
              )
            }
          />
        )}
        {app && editor?.kind === "relation" && (
          <RelationEditor
            relation={editor.relation}
            party={editor.party!}
            busy={locked}
            close={() => setEditor(null)}
            save={(body) =>
              execute(
                base +
                  "/" +
                  applicationId +
                  "/relations" +
                  (editor.relation ? "/" + editor.relation.relationId : ""),
                editor.relation ? "PATCH" : "POST",
                body,
              )
            }
          />
        )}
        {app && upload && (
          <UploadEditor
            app={app}
            subject={upload.subject}
            requirement={upload.requirement}
            resume={upload.resume}
            client={client}
            close={() => setUpload(null)}
            done={async () => {
              await reload();
            }}
          />
        )}
      </Shell>
    </CommandFeedback.Provider>
  );
}
