"use client";

import { Fragment, useMemo, useState } from "react";
import {
  ACTORS,
  FUNCTIONAL_SYSTEMS,
  GLOBAL_USE_CASES,
  type InterfaceUseCase,
  type UseCase,
} from "../content/requirements";
import { StatusBadge } from "./status-badge";
import { GLOBAL_USE_CASE_SEQUENCES } from "../content/global-use-case-sequences.mjs";
import { toggleUseCaseSelection } from "../lib/use-case-selection.mjs";
import { GlobalUseCaseSequenceDiagram } from "./global-use-case-sequence";

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

function GlobalUseCaseRows({
  selectedUseCaseId,
  onSelect,
}: {
  selectedUseCaseId: string | null;
  onSelect: (useCaseId: string) => void;
}) {
  return (
    <tbody>
      {GLOBAL_USE_CASES.map((useCase) => {
        const isSelected = selectedUseCaseId === useCase.id;
        return (
          <Fragment key={useCase.id}>
            <tr
              className={`global-use-case-row${isSelected ? " is-selected" : ""}`}
              onClick={() => onSelect(useCase.id)}
            >
              <td className="uc-code">{useCase.id}</td>
              <td>
                <button
                  className="global-use-case-trigger"
                  type="button"
                  aria-expanded={isSelected}
                  aria-controls={`sequence-${useCase.id}`}
                  aria-label={`查看 ${useCase.id} 时序图`}
                  onClick={(event) => {
                    event.stopPropagation();
                    onSelect(useCase.id);
                  }}
                >
                  <strong>{useCase.title}</strong>
                  <span>{isSelected ? "收起时序图" : "查看时序图"} <b aria-hidden="true">{isSelected ? "↑" : "→"}</b></span>
                </button>
              </td>
              <td>{actorNames(useCase)}</td>
              <td>{useCase.result}</td>
              <td><StatusBadge status={useCase.status} /></td>
            </tr>
            {isSelected ? (
              <tr className="sequence-detail-row">
                <td colSpan={5}>
                  <GlobalUseCaseSequenceDiagram
                    useCaseId={useCase.id}
                    title={useCase.title}
                    sequence={GLOBAL_USE_CASE_SEQUENCES[useCase.id]}
                    onClose={() => onSelect(useCase.id)}
                  />
                </td>
              </tr>
            ) : null}
          </Fragment>
        );
      })}
    </tbody>
  );
}

export function GlobalUseCases() {
  const [selectedUseCaseId, setSelectedUseCaseId] = useState<string | null>(null);

  function toggleSequence(useCaseId: string) {
    setSelectedUseCaseId((currentUseCaseId) => toggleUseCaseSelection(currentUseCaseId, useCaseId));
  }

  return (
    <div className="global-use-cases">
      <p className="global-use-case-help">点击任意全局用例查看系统调用时序图；图中同时包含入口应用、核心系统和外部参与方。</p>
      <div className="table-wrap">
        <table className="use-case-table global-use-case-table">
          <thead><tr><th>ID</th><th>端到端用例</th><th>参与者</th><th>成功结果</th><th>状态</th></tr></thead>
          <GlobalUseCaseRows selectedUseCaseId={selectedUseCaseId} onSelect={toggleSequence} />
        </table>
      </div>
    </div>
  );
}

export function UseCaseViewOverview() {
  const independentUseCaseCount = FUNCTIONAL_SYSTEMS.reduce((sum, system) => sum + system.useCases.length, 0);
  const interfaceUseCaseCount = FUNCTIONAL_SYSTEMS.reduce((sum, system) => sum + system.interfaceUseCases.length, 0);

  return (
    <header className="use-case-view-hero">
      <div className="eyebrow">USE CASE CATALOG</div>
      <h1>Global &amp; System Use Cases</h1>
      <p>先从跨系统业务目标理解完整链路，再进入六个系统查看独立用例、接口用例和协作边界。</p>
      <div className="use-case-view-stats" aria-label="用例目录统计">
        <article><small>GLOBAL</small><strong>{GLOBAL_USE_CASES.length}</strong><span>全局用例</span></article>
        <article><small>SYSTEMS</small><strong>{FUNCTIONAL_SYSTEMS.length}</strong><span>核心系统</span></article>
        <article><small>INDEPENDENT</small><strong>{independentUseCaseCount}</strong><span>独立用例</span></article>
        <article><small>INTERFACE</small><strong>{interfaceUseCaseCount}</strong><span>接口用例</span></article>
      </div>
    </header>
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
