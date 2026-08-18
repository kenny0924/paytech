import { ENTRY_APPLICATIONS } from "../content/requirements";
import { StatusBadge } from "./status-badge";

export function EntryApplicationCatalog() {
  return (
    <div className="entry-applications">
      <div className="entry-boundary">
        <strong>入口应用不是核心系统</strong>
        <p>它们只负责身份接入、页面交互和接口编排；核心业务事实仍由六个系统拥有，禁止入口应用建立旁路业务库或跨库访问。</p>
      </div>

      <div className="entry-app-grid">
        {ENTRY_APPLICATIONS.map((application, index) => (
          <article className="entry-app-card" key={application.code}>
            <header>
              <span className="entry-app-number">{String(index + 1).padStart(2, "0")}</span>
              <div>
                <small>{application.kind === "human" ? "HUMAN ENTRY" : "MACHINE ENTRY"} · {application.audience}</small>
                <h3>{application.name}</h3>
              </div>
              <StatusBadge status={application.status} />
            </header>
            <p className="entry-app-purpose">{application.purpose}</p>

            <div className="entry-app-detail">
              <div>
                <strong>主要能力</strong>
                <ul>{application.capabilities.map((capability) => <li key={capability}>{capability}</li>)}</ul>
              </div>
              <div>
                <strong>操作边界</strong>
                <ul>{application.restrictions.map((restriction) => <li key={restriction}>{restriction}</li>)}</ul>
              </div>
            </div>

            <div className="entry-app-connections">
              <small>调用核心系统</small>
              <div>{application.connectsTo.map((system) => <span key={system}>{system}</span>)}</div>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
