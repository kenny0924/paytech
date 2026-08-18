"use client";

import { useMemo, useState } from "react";
import {
  ACTORS,
  FUNCTIONAL_SYSTEMS,
  GLOBAL_USE_CASES,
  type InterfaceUseCase,
  type UseCase,
} from "../content/requirements";
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

function InterfaceUseCaseRows({ useCases }: { useCases: InterfaceUseCase[] }) {
  return (
    <tbody>
      {useCases.map((useCase) => (
        <tr key={useCase.id}>
          <td className="uc-code">{useCase.id}</td>
          <td><strong>{useCase.title}</strong></td>
          <td>{useCase.consumers.join(" · ")}</td>
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
    return FUNCTIONAL_SYSTEMS
      .filter((item) => system === "all" || item.code === system)
      .map((item) => ({
        ...item,
        useCases: item.useCases.filter((useCase) => actor === "all" || useCase.actorIds.includes(actor)),
      }))
      .filter((item) => actor === "all" || item.useCases.length > 0);
  }, [actor, system]);

  const independentUseCaseCount = visibleSystems.reduce((sum, item) => sum + item.useCases.length, 0);
  const interfaceUseCaseCount = visibleSystems.reduce((sum, item) => sum + item.interfaceUseCases.length, 0);

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
            {FUNCTIONAL_SYSTEMS.map((item) => <option value={item.code} key={item.code}>{item.name}</option>)}
          </select>
        </label>
        <button type="button" onClick={() => { setActor("all"); setSystem("all"); }}>清除筛选</button>
        <span>{independentUseCaseCount} 独立用例 · {interfaceUseCaseCount} 接口用例</span>
      </div>

      {visibleSystems.length === 0 ? (
        <div className="empty-state">没有符合当前参与者和系统组合的 Use Case，请清除筛选后重试。</div>
      ) : visibleSystems.map((item, index) => (
        <details className="system-card" key={item.code} open={index < 2}>
          <summary>
            <span className="system-number">{String(FUNCTIONAL_SYSTEMS.findIndex((systemItem) => systemItem.code === item.code) + 1).padStart(2, "0")}</span>
            <div><h3>{item.name}</h3><p>{item.responsibility}</p></div>
            <span className="truth-label">SoT · {item.sourceOfTruth}</span>
          </summary>
          <div className="system-body">
            <h4 className="system-subheading">独立用例</h4>
            <div className="table-wrap">
              <table className="use-case-table">
                <thead><tr><th>ID</th><th>Use Case</th><th>Actor</th><th>成功结果</th><th>状态</th></tr></thead>
                <UseCaseRows useCases={item.useCases} />
              </table>
            </div>
            <h4 className="system-subheading">接口用例</h4>
            <div className="table-wrap">
              <table className="use-case-table">
                <thead><tr><th>ID</th><th>Interface Use Case</th><th>调用方</th><th>成功结果</th><th>状态</th></tr></thead>
                <InterfaceUseCaseRows useCases={item.interfaceUseCases} />
              </table>
            </div>
            <div className="system-contracts">
              <div><small>内部功能域</small>{item.internalDomains.map((value) => <span key={value}>{value}</span>)}</div>
              <div><small>同步接口依赖</small>{item.dependsOn.map((value) => <span key={value}>{value}</span>)}</div>
              <div><small>订阅 MQ</small>{item.subscribes.map((value) => <span key={value}>{value}</span>)}</div>
              <div><small>发布 MQ 通知</small>{item.publishes.map((value) => <span key={value}>{value}</span>)}</div>
              <div><small>不负责</small>{item.exclusions.map((value) => <span key={value}>{value}</span>)}</div>
            </div>
          </div>
        </details>
      ))}
    </div>
  );
}
