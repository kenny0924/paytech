import { ENTRY_APPLICATIONS } from "../content/requirements";
import { StatusBadge } from "./status-badge";

const ENTRY_KIND_LABEL = {
  human: "HUMAN ENTRY",
  machine: "MACHINE ENTRY",
  infrastructure: "EDGE INFRASTRUCTURE",
};

export function EntryApplicationCatalog() {
  return (
    <div className="entry-applications">
      <div className="entry-boundary">
        <strong>入口层不是核心系统</strong>
        <p>Gateway 负责安全接入和路由，入口应用负责页面或外部协议；核心业务事实仍由六个系统拥有，入口层不得建立旁路业务库。</p>
      </div>

      <div className="entry-app-grid">
        {ENTRY_APPLICATIONS.map((application, index) => (
          <article className={`entry-app-card entry-app-card--${application.kind}`} key={application.code}>
            <header>
              <span className="entry-app-number">{String(index + 1).padStart(2, "0")}</span>
              <div>
                <small>{ENTRY_KIND_LABEL[application.kind]} · {application.audience}</small>
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
              <small>主要连接</small>
              <div>{application.connectsTo.map((system) => <span key={system}>{system}</span>)}</div>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
