"use client";

import { useMemo, useState } from "react";
import { ACTORS, GLOBAL_USE_CASES, SYSTEMS, type UseCase } from "../content/requirements";
import { StatusBadge } from "./status-badge";

function actorNames(useCase: UseCase) {
  return useCase.actorIds
    .map((id) => ACTORS.find((actor) => actor.id === id)?.name ?? id)
    .join(" · ");
}

function UseCaseRows({ useCases }: { useCases: UseCase[] }) {
  return (
    <tbody>
      {useCases.map((useCase) => (
        <tr key={useCase.id}>
          <td className="uc-code">{useCase.id}</td>
          <td><strong>{useCase.title}</strong></td>
          <td>{actorNames(useCase)}</td>
          <td>{useCase.result}</td>
          <td><StatusBadge status={useCase.status} /></td>
        </tr>
      ))}
    </tbody>
  );
}

export function GlobalUseCases() {
  return (
    <div className="table-wrap">
      <table className="use-case-table">
        <thead><tr><th>ID</th><th>端到端用例</th><th>参与者</th><th>成功结果</th><th>状态</th></tr></thead>
        <UseCaseRows useCases={GLOBAL_USE_CASES} />
      </table>
    </div>
  );
}

export function UseCaseCatalog() {
  const [actor, setActor] = useState("all");
  const [system, setSystem] = useState("all");

  const visibleSystems = useMemo(() => {
    return SYSTEMS
      .filter((item) => system === "all" || item.code === system)
      .map((item) => ({
        ...item,
        useCases: item.useCases.filter((useCase) => actor === "all" || useCase.actorIds.includes(actor)),
      }))
      .filter((item) => item.useCases.length > 0);
  }, [actor, system]);

  return (
    <div className="catalog">
      <div className="filter-bar" aria-label="Use Case 筛选">
        <label>参与者
          <select value={actor} onChange={(event) => setActor(event.target.value)}>
            <option value="all">全部参与者</option>
            {ACTORS.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}
          </select>
        </label>
        <label>系统
          <select value={system} onChange={(event) => setSystem(event.target.value)}>
            <option value="all">全部系统</option>
            {SYSTEMS.map((item) => <option value={item.code} key={item.code}>{item.name}</option>)}
          </select>
        </label>
        <button type="button" onClick={() => { setActor("all"); setSystem("all"); }}>清除筛选</button>
        <span>{visibleSystems.reduce((sum, item) => sum + item.useCases.length, 0)} Use Cases</span>
      </div>

      {visibleSystems.length === 0 ? (
        <div className="empty-state">没有符合当前参与者和系统组合的 Use Case，请清除筛选后重试。</div>
      ) : visibleSystems.map((item, index) => (
        <details className="system-card" key={item.code} open={index < 2}>
          <summary>
            <span className="system-number">{String(SYSTEMS.findIndex((systemItem) => systemItem.code === item.code) + 1).padStart(2, "0")}</span>
            <div><h3>{item.name}</h3><p>{item.responsibility}</p></div>
            <span className="truth-label">SoT · {item.sourceOfTruth}</span>
          </summary>
          <div className="system-body">
            <div className="table-wrap">
              <table className="use-case-table">
                <thead><tr><th>ID</th><th>Use Case</th><th>Actor</th><th>成功结果</th><th>状态</th></tr></thead>
                <UseCaseRows useCases={item.useCases} />
              </table>
            </div>
            <div className="system-contracts">
              <div><small>PROVIDES</small>{item.provides.map((value) => <span key={value}>{value}</span>)}</div>
              <div><small>CONSUMES</small>{item.consumes.map((value) => <span key={value}>{value}</span>)}</div>
              <div><small>PUBLISHES</small>{item.publishes.map((value) => <span key={value}>{value}</span>)}</div>
            </div>
          </div>
        </details>
      ))}
    </div>
  );
}
