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
import { changeCatalogField, catalogChoices } from "./catalog.ts";
import type { FormOptions } from "./catalog.ts";
import type { createAuthClient } from "./auth.ts";
import {
  base,
  files,
  patch,
  flatten,
  relationPatch,
  createCommand,
  newUpload,
  runUpload,
  readCheckpoint,
  downloadUrl,
  statuses,
  scanLabels,
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
type Field = [string, string, string?, number?];
const individual: Field[] = [
  ["individual.givenName", "名", "text", 64],
  ["individual.middleName", "中间名（选填）", "text", 64],
  ["individual.familyName", "姓", "text", 64],
  ["individual.birthDate", "出生日期", "date"],
  ["individual.nationalityCountry", "国籍", "country"],
  ["individual.residenceCountry", "居住国家 / 地区", "country"],
];
const organization: Field[] = [
  ["organization.legalName", "组织法定名称", "text", 64],
  ["organization.tradeName", "组织简称（选填）", "text", 64],
  ["organization.incorporationCountry", "注册国家 / 地区", "country"],
  ["organization.organizationType", "组织类型", "catalog", 64],
  ["organization.incorporationDate", "成立日期", "date"],
];
const address: Field[] = [
  ["address.addressLine1", "地址第一行", "text", 255],
  ["address.addressLine2", "地址第二行（选填）", "text", 255],
  ["address.city", "城市", "text", 128],
  ["address.stateOrProvince", "州 / 省", "text", 128],
  ["address.postalCode", "邮政编码", "text", 32],
  ["address.countryCode", "地址所在国家 / 地区", "country"],
];
const identifier: Field[] = [
  ["identifier.identifierType", "证件 / 登记证明类型", "catalog", 64],
  ["identifier.identifierValue", "证件 / 注册编号", "text", 128],
  [
    "identifier.issuingCountry",
    "签发国家 / 地区（由机构确定）",
    "issuer-country",
  ],
  ["identifier.issuerScopeKey", "签发 / 登记机构", "catalog", 64],
  ["identifier.validFrom", "有效期开始", "date"],
  ["identifier.validTo", "有效期结束（不含当日，无固定期限可留空）", "date"],
];
const business: Field[] = [
  ["business.businessModelCode", "经营模式", "catalog", 64],
  ["business.websiteUrl", "网站地址（选填）", "url", 2048],
  ["business.expectedVolume", "预计月交易额", "text", 25],
  ["business.expectedTicket", "预计客单价", "text", 25],
  ["business.currency", "交易币种", "currency"],
  ["contact.email", "业务联系邮箱", "email", 320],
  ["contact.phone", "业务联系电话（含国际区号）", "tel", 32],
  ["business.businessDescription", "业务说明", "textarea", 2000],
];
const countryCodes =
  "AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW".split(
    " ",
  );
const regionNames = new Intl.DisplayNames(["zh-Hans"], { type: "region" });
const countries = countryCodes
  .map((code) => [code, regionNames.of(code) ?? code])
  .sort((a, b) => a[1].localeCompare(b[1], "zh"));
const relationNames: Record<string, string> = {
  OWNERSHIP: "股东",
  CONTROL: "控制人",
  DIRECTOR: "董事",
  SIGNATORY: "授权签字人",
  UBO: "最终受益人",
};
const materialNames: Record<string, string> = {
  PASSPORT: "护照",
  IDENTITY_DOCUMENT: "身份证明",
  IDENTITY: "身份证明",
  REGISTRATION_CERTIFICATE: "组织注册证明",
  ADDRESS_PROOF: "地址证明",
  PROOF_OF_ADDRESS: "地址证明",
};
function Failure({ error }: { error: Error | null }) {
  const ref = useRef<HTMLDivElement>(null);
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
        <small>请求编号：{error.requestId}</small>
      )}
    </div>
  ) : null;
}
function Shell({
  children,
  protect = false,
}: {
  children: ReactNode;
  protect?: boolean;
}) {
  return (
    <>
      <div
        className="console-bar"
        onClickCapture={(event) => {
          if (
            protect &&
            event.target instanceof Element &&
            event.target.closest("a") &&
            !window.confirm("离开当前申请？未保存的修改将丢失。")
          ) {
            event.preventDefault();
            event.stopPropagation();
          }
        }}
      >
        <Link to="/onboarding" className="console-brand">
          PAYTECH <span>/ 客户控制台</span>
        </Link>
        <Link to="/account">账号</Link>
      </div>
      <main className="onboarding">{children}</main>
    </>
  );
}
const FormCatalog = createContext<FormOptions | null>(null);
function useFormCatalog(
  client: Client,
  type: Application["clientType"],
  country: string,
) {
  const key = type + ":" + country;
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
        "/onboarding/v1/form-options?clientType=" +
          type +
          (country ? "&countryCode=" + encodeURIComponent(country) : ""),
      )
      .then((data) => {
        if (
          data.clientType !== type ||
          (data.countryCode ?? "") !== country ||
          !["AVAILABLE", "COUNTRY_REQUIRED", "UNAVAILABLE"].includes(
            data.catalogStatus,
          ) ||
          !Array.isArray(data.organizationTypes) ||
          !Array.isArray(data.businessModels) ||
          !Array.isArray(data.identifierTypes)
        ) {
          throw new Error("表单选项暂时不可用");
        }
        if (current) setState({ key, data, error: null });
      })
      .catch((error: Error) => {
        if (current) setState({ key, data: null, error });
      });
    return () => {
      current = false;
    };
  }, [client, type, country, attempt]);
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
  if (catalog.loading)
    return (
      <p className="hint" role="status">
        正在读取表单选项…
      </p>
    );
  if (catalog.data?.catalogStatus === "AVAILABLE") return null;
  return (
    <div className="notice" role="status">
      {catalog.data?.catalogStatus === "COUNTRY_REQUIRED"
        ? "选择注册国家 / 地区后，可查看适用的组织类型。"
        : "部分表单选项暂未开放，已有资料会保留，其他内容可以继续保存。"}
      {catalog.error && <small>{catalog.error.message}</small>}
      <button
        type="button"
        className="secondary compact"
        onClick={catalog.retry}
      >
        重新读取选项
      </button>
    </div>
  );
}
function Fields({
  items,
  values,
  set,
}: {
  items: Field[];
  values: Flat;
  set: (key: string, value: string) => void;
}) {
  const prefix = useId();
  const catalog = useContext(FormCatalog);
  return (
    <div className="form-grid">
      {items.map(([key, label, kind = "text", max = 64]) => (
        <div
          className={"field " + (kind === "textarea" ? "wide" : "")}
          key={key}
        >
          <label htmlFor={prefix + key}>{label}</label>
          {kind === "catalog" ? (
            <select
              id={prefix + key}
              value={values[key] ?? ""}
              disabled={!catalogChoices(key, values, catalog).length}
              onChange={(event) => set(key, event.target.value)}
            >
              <option value="">
                {catalogChoices(key, values, catalog).length
                  ? "请选择"
                  : "暂无可选项"}
              </option>
              {values[key] &&
                !catalogChoices(key, values, catalog).some(
                  (option) => option.code === values[key],
                ) && (
                  <option value={values[key]} disabled>
                    原值：{values[key]}（待确认）
                  </option>
                )}
              {catalogChoices(key, values, catalog).map((option) => (
                <option key={option.code} value={option.code}>
                  {option.label}
                </option>
              ))}
            </select>
          ) : kind === "issuer-country" ? (
            <input
              id={prefix + key}
              readOnly
              value={
                values[key]
                  ? (regionNames.of(values[key]) ?? values[key])
                  : values["identifier.issuerScopeKey"] === "GLOBAL"
                    ? "全球标识"
                    : "请先选择签发 / 登记机构"
              }
            />
          ) : kind === "country" || kind === "currency" ? (
            <select
              id={prefix + key}
              value={values[key] ?? ""}
              onChange={(event) => set(key, event.target.value)}
            >
              <option value="">请选择</option>
              {(kind === "country"
                ? countries
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
              rows={4}
              maxLength={max}
              value={values[key] ?? ""}
              onChange={(event) => set(key, event.target.value)}
            />
          ) : (
            <input
              id={prefix + key}
              type={kind}
              maxLength={max}
              value={values[key] ?? ""}
              onChange={(event) => set(key, event.target.value)}
            />
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
    [message, setMessage] = useState("");
  const catalog = useFormCatalog(
    client,
    app.clientType,
    values[
      app.clientType === "ORGANIZATION"
        ? "organization.incorporationCountry"
        : "individual.residenceCountry"
    ] ?? "",
  );
  useEffect(() => {
    const next = flatten(app.values);
    if (JSON.stringify(next) !== JSON.stringify(before)) {
      setBefore(next);
      setValues(next);
      setMessage("资料已同步");
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
    setValues((old) => changeCatalogField(old, key, value, catalog.data));
  };
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!dirty) return;
    try {
      const updated = await save(changes);
      const next = flatten(updated.values);
      setBefore(next);
      setValues(next);
      setMessage("资料已保存");
    } catch {
      /* 页面统一显示可重试错误。 */
    }
  }
  return (
    <FormCatalog.Provider value={catalog.data}>
      <form onSubmit={submit}>
        <CatalogNotice catalog={catalog} />
        <fieldset disabled={!app.editable || busy}>
          <section id="profile" className="form-section">
            <h2>
              {app.clientType === "ORGANIZATION" ? "组织资料" : "个人资料"}
            </h2>
            <Fields
              items={
                app.clientType === "ORGANIZATION" ? organization : individual
              }
              values={values}
              set={set}
            />
            <h3>
              {app.clientType === "ORGANIZATION" ? "注册地址" : "居住地址"}
            </h3>
            <Fields items={address} values={values} set={set} />
            <h3>身份证明</h3>
            <Fields items={identifier} values={values} set={set} />
          </section>
          <section id="business" className="form-section">
            <h2>经营资料</h2>
            <p>描述实际销售的商品或提供的服务，便于审核人员了解业务。</p>
            <Fields items={business} values={values} set={set} />
          </section>
        </fieldset>
        {app.editable && (
          <div className="form-actions">
            <span role="status">
              {message || (dirty ? "有未保存的修改" : "资料已同步")}
            </span>
            <button className="primary compact" disabled={busy || !dirty}>
              保存资料
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
  const ref = useRef<HTMLDialogElement>(null);
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
          aria-label="关闭编辑"
        >
          关闭
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
          重试原操作
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
  return (
    <>
      <div className="field">
        <label htmlFor="relationType">与组织的关系</label>
        <select
          id="relationType"
          value={values.relationType ?? ""}
          required
          onChange={(e) => {
            set("relationType", e.target.value);
            set("ownershipPercentage", "");
          }}
        >
          <option value="">请选择</option>
          {Object.entries(relationNames).map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <div className="form-grid">
        {["OWNERSHIP", "UBO"].includes(values.relationType) && (
          <div className="field">
            <label htmlFor="ownershipPercentage">持有比例（%）</label>
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
          <label htmlFor="effectiveFrom">生效日期</label>
          <input
            id="effectiveFrom"
            type="date"
            required
            value={values.effectiveFrom ?? ""}
            onChange={(e) => set("effectiveFrom", e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="effectiveTo">结束日期（选填）</label>
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
  const catalog = useFormCatalog(
    client,
    "INDIVIDUAL",
    values["individual.residenceCountry"] ?? "",
  );
  const dirty = patch(before, values).dirtyFields.length > 0;
  const exit = () => {
    if (!busy && (!dirty || window.confirm("放弃尚未保存的人员修改？")))
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
    <Modal title={person ? "编辑人员" : "添加人员"} close={exit}>
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
            <h3>居住地址</h3>
            <Fields
              items={address}
              values={values}
              set={(key, value) =>
                setValues((old) =>
                  changeCatalogField(old, key, value, catalog.data),
                )
              }
            />
            <h3>身份证明</h3>
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
                <h3>组织关系</h3>
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
            保存人员
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
      title={relation ? "编辑关系" : "添加关系"}
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
          保存关系
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
  const guard = useRef(false);
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
            throw new RequestFailure(401, "登录状态已变更，请重新打开申请");
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
      title="上传申请材料"
      close={() => {
        if (!busy) close();
      }}
    >
      <p>
        {materialNames[requirement.documentType] ?? requirement.documentType}
      </p>
      <p className="notice">
        文件安全扫描通过后才会关联到申请。您可以稍后回来继续处理。
      </p>
      {phase === "PUT" && (
        <div className="field">
          <label htmlFor="material-file">选择文件</label>
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
          ? "处理中…"
          : phase === "SCAN"
            ? "文件已上传，等待扫描结果"
            : phase === "COMPLETE"
              ? "等待确认文件上传完成"
              : phase === "ASSOCIATE"
                ? "文件可用，等待关联到申请"
                : phase === "DONE"
                  ? "材料已关联"
                  : file
                    ? "文件已选择"
                    : "请选择文件"}
      </p>
      <Failure error={error} />
      <button
        className="primary"
        onClick={advance}
        disabled={busy || (phase === "PUT" && !file)}
      >
        {phase === "SCAN"
          ? "检查扫描结果并关联"
          : error
            ? "重试原操作"
            : "继续"}
      </button>
      {!busy && (
        <button
          className="secondary full"
          onClick={() => {
            if (
              window.confirm(
                "放弃这次未完成的材料处理？已关联的材料不会被删除。",
              )
            ) {
              sessionStorage.removeItem(storageKey);
              close();
            }
          }}
        >
          放弃本次处理
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
    [pending, setPending] = useState(false);
  const command = useRef<ReturnType<typeof createCommand> | null>(null),
    guard = useRef(false);
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
    if (!validId) throw Error("申请不存在");
    const value = await client.request<Application>(base + "/" + applicationId);
    setApp(value);
    return value;
  }
  function fail(failure: unknown) {
    const value = failure instanceof Error ? failure : new Error("操作未完成");
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
        : Promise.reject(Error("申请不存在"))
      : client.request<Start>("/onboarding/v1/start");
    request
      .then((value) => {
        if (alive) {
          if (applicationId) setApp(value as Application);
          else setStart(value as Start);
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
  const locked = busy || pending;
  async function execute(
    path: string,
    method: "POST" | "PATCH" | "DELETE",
    body?: unknown,
  ): Promise<Application> {
    if (guard.current) throw Error("请等待当前操作完成");
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
    else setError(new Error("没有可恢复的材料处理记录"));
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
      !window.confirm(
        "存在一份尚未完成的材料。开始新上传将替换本页的恢复记录，是否继续？",
      )
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
      .join(" ") || "待补充姓名";
  return (
    <CommandFeedback.Provider
      value={{ error, pending, busy, retry: () => void retry() }}
    >
      <Shell protect={dirty || pending}>
        <div className="page-heading">
          <div>
            <h1>
              {app
                ? (app.clientType === "ORGANIZATION" ? "组织" : "个人") +
                  "入驻申请"
                : "我的入驻申请"}
            </h1>
            <p>
              {app
                ? "填写后可保存，稍后继续补充资料。"
                : "选择适合您的客户身份，开始填写入驻资料。"}
            </p>
          </div>
          {app && (
            <span className="status">
              {statuses[app.status] ?? "状态待确认"}
            </span>
          )}
        </div>
        {app && (
          <nav className="section-nav">
            <a href="#profile">基础资料</a>
            <a href="#business">经营资料</a>
            {app.clientType === "ORGANIZATION" && (
              <a href="#people">相关人员</a>
            )}
            <a href="#materials">申请材料</a>
            <Link
              to="/onboarding"
              onClick={(event) => {
                if (
                  (dirty || pending) &&
                  !window.confirm("离开当前申请？未保存的修改将丢失。")
                )
                  event.preventDefault();
              }}
            >
              返回申请列表
            </Link>
          </nav>
        )}
        <Failure error={error} />
        {pending && (
          <div className="notice" role="status">
            上次操作结果尚未确认，请重试原操作。
            <button className="secondary" disabled={busy} onClick={retry}>
              重试原操作
            </button>
          </div>
        )}
        {loading && <p role="status">正在读取…</p>}
        {!loading && error && !app && !start && (
          <button
            className="secondary"
            onClick={() => window.location.reload()}
          >
            重新读取
          </button>
        )}
        {start && (
          <>
            <div className="application-list">
              {start.applications.map((row) => (
                <Link
                  className="application-row"
                  key={row.applicationId}
                  to={"/onboarding/applications/" + row.applicationId}
                >
                  <div>
                    <strong>
                      {row.clientType === "ORGANIZATION" ? "组织" : "个人"}入驻
                    </strong>
                    <small>{row.applicationNo}</small>
                  </div>
                  <span>{statuses[row.status] ?? "查看申请"} →</span>
                </Link>
              ))}
            </div>
            {start.hasMore && <p>这里只展示最近 20 份申请。</p>}
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
                        ? "以个人身份入驻"
                        : "以组织身份入驻"}
                    </strong>
                    <span>
                      {type === "INDIVIDUAL"
                        ? "填写个人身份与经营资料"
                        : "填写组织资料、经营资料及相关人员"}
                    </span>
                    <b>开始填写 →</b>
                  </button>
                ))}
              </div>
            )}
          </>
        )}
        {app && (
          <>
            {!app.editable && (
              <p className="notice">
                当前申请为只读状态。审核通过或最终拒绝后不能修改。
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
                <h2>相关人员</h2>
                <p>补充与组织有关的自然人及其关系。同一人员可以有多种关系。</p>
                {dirty && (
                  <p className="notice">请先保存基础资料，再管理人员与关系。</p>
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
                            ? "编辑人员"
                            : "查看人员"}
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
                            添加关系
                          </button>
                        )}
                      </div>
                    </div>
                    {app.relations
                      .filter((r) => r.relatedPartyId === person.partyId)
                      .map((relation) => (
                        <div className="relation-row" key={relation.relationId}>
                          <span>
                            {relationNames[relation.relationType] ??
                              relation.relationType}
                            {relation.ownershipPercentage
                              ? " · " + relation.ownershipPercentage + "%"
                              : ""}
                            {relation.status === "REMOVED" ? " · 已移除" : ""}
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
                                编辑关系
                              </button>
                              <button
                                className="text-button"
                                disabled={locked || dirty}
                                onClick={() => {
                                  if (
                                    window.confirm(
                                      "移除此人员在当前申请中的这条关系？",
                                    )
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
                                移除
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
                    ＋ 添加人员
                  </button>
                )}
              </section>
            )}
            <section id="materials" className="form-section">
              <div className="row-heading">
                <h2>申请材料</h2>
                {app.editable && (
                  <button
                    className="text-button"
                    disabled={locked || dirty}
                    onClick={resumeUpload}
                  >
                    继续未完成的上传
                  </button>
                )}
              </div>
              <p>材料要求依据已保存的资料显示。文件通过安全扫描后才可使用。</p>
              {app.materialSubjects.map((subject) => (
                <div className="material-subject" key={subject.subjectPartyId}>
                  <h3>
                    {subject.subjectPartyId === app.subjectPartyId
                      ? "主体材料"
                      : personName(
                          app.people.find(
                            (p) => p.partyId === subject.subjectPartyId,
                          )!,
                        )}
                  </h3>
                  {subject.requirementsStatus !== "AVAILABLE" ? (
                    <p className="notice">
                      {subject.requirementsStatus === "COUNTRY_REQUIRED"
                        ? "请填写并保存国家 / 地区，以读取材料要求。"
                        : "材料要求暂时不可用，请稍后重新读取。"}
                    </p>
                  ) : (
                    subject.requirements.map((requirement) => (
                      <div
                        className="material-row"
                        key={requirement.requirementCode}
                      >
                        <span>
                          {materialNames[requirement.documentType] ??
                            requirement.documentType}
                          <small>
                            {requirement.requiredFlag ? "必需材料" : "按需提供"}
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
                            上传文件
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
                    <strong>{doc.file?.displayName ?? "材料文件"}</strong>
                    <small>
                      {!doc.subjectInScope ? "人员已不在申请范围 · " : ""}
                      {doc.fileStatus !== "AVAILABLE"
                        ? "文件状态暂不可用"
                        : doc.file?.objectStatus !== "ACTIVE"
                          ? "文件不可用"
                          : (scanLabels[doc.file?.scanStatus ?? ""] ??
                            "状态待确认")}
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
                          查看
                        </button>
                      )}
                    {app.editable && (
                      <button
                        className="text-button"
                        disabled={locked || dirty}
                        onClick={() => {
                          if (window.confirm("从当前申请移除这份材料？"))
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
                        移除
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
                重新读取材料状态
              </button>
            </section>
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
