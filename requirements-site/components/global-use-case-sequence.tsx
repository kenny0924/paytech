import {
  SEQUENCE_PARTICIPANTS,
  type GlobalUseCaseSequence,
  type SequenceParticipant,
} from "../content/global-use-case-sequences.mjs";

const LANE_GAP = 150;
const SIDE_PADDING = 76;
const HEADER_HEIGHT = 78;
const MESSAGE_HEIGHT = 58;

function participantX(index: number) {
  return SIDE_PADDING + index * LANE_GAP;
}

function participantLabelLines(label: string) {
  if (label.length <= 18) return [label];

  const words = label.split(" ");
  if (words.length === 1) return [label.slice(0, 16), label.slice(16)];

  const splitAt = Math.ceil(words.length / 2);
  return [words.slice(0, splitAt).join(" "), words.slice(splitAt).join(" ")];
}

function ParticipantHeader({ participant, x }: { participant: SequenceParticipant; x: number }) {
  const lines = participantLabelLines(participant.name);

  return (
    <g className={`sequence-participant sequence-participant--${participant.type}`}>
      <rect x={x - 64} y="10" width="128" height="46" rx="9" />
      <text x={x} y={lines.length === 1 ? 38 : 31} textAnchor="middle">
        {lines.map((line, index) => (
          <tspan x={x} dy={index === 0 ? 0 : 13} key={line}>{line}</tspan>
        ))}
      </text>
    </g>
  );
}

function MessageArrow({
  fromX,
  toX,
  y,
  label,
  kind,
  markerId,
}: {
  fromX: number;
  toX: number;
  y: number;
  label: string;
  kind: "sync" | "async";
  markerId: string;
}) {
  if (fromX === toX) {
    const loopWidth = 42;
    const path = `M ${fromX} ${y} h ${loopWidth} v 24 h ${-loopWidth}`;
    return (
      <g className={`sequence-message sequence-message--${kind}`}>
        <path d={path} markerEnd={`url(#${markerId})`} />
        <text x={fromX + loopWidth / 2} y={y - 8} textAnchor="middle">{label}</text>
      </g>
    );
  }

  const direction = toX > fromX ? 1 : -1;
  const startX = fromX + 6 * direction;
  const endX = toX - 6 * direction;
  return (
    <g className={`sequence-message sequence-message--${kind}`}>
      <line x1={startX} y1={y} x2={endX} y2={y} markerEnd={`url(#${markerId})`} />
      <text x={(fromX + toX) / 2} y={y - 8} textAnchor="middle">{label}</text>
    </g>
  );
}

export function GlobalUseCaseSequenceDiagram({
  useCaseId,
  title,
  sequence,
  onClose,
}: {
  useCaseId: string;
  title: string;
  sequence: GlobalUseCaseSequence;
  onClose: () => void;
}) {
  const participants = sequence.participants.map((id) => ({ id, ...SEQUENCE_PARTICIPANTS[id] }));
  const participantIndexes = new Map(participants.map((participant, index) => [participant.id, index]));
  const width = SIDE_PADDING * 2 + Math.max(participants.length - 1, 1) * LANE_GAP;
  const height = HEADER_HEIGHT + sequence.messages.length * MESSAGE_HEIGHT + 50;
  const markerId = `sequence-arrow-${useCaseId.toLowerCase()}`;
  const titleId = `sequence-title-${useCaseId.toLowerCase()}`;

  return (
    <section className="sequence-panel" id={`sequence-${useCaseId}`} aria-labelledby={titleId}>
      <header className="sequence-panel-header">
        <div>
          <span>{useCaseId} · SYSTEM CALL SEQUENCE</span>
          <h3 id={titleId}>{title}</h3>
          <p>{sequence.summary}</p>
        </div>
        <button type="button" onClick={onClose} aria-label={`关闭 ${useCaseId} 时序图`}>收起</button>
      </header>

      <div className="sequence-legend" aria-label="时序图图例">
        <span className="legend-actor">业务参与者</span>
        <span className="legend-gateway">API Gateway</span>
        <span className="legend-entry">入口应用</span>
        <span className="legend-core">核心系统</span>
        <span className="legend-external">外部系统</span>
        <span className="legend-sync">同步接口</span>
        <span className="legend-async">MQ / 异步回调</span>
      </div>

      <div className="sequence-scroll" aria-label={`${useCaseId} 系统调用时序图，可横向滚动`}>
        <svg className="sequence-svg" width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-labelledby={titleId}>
          <defs>
            <marker id={markerId} markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto" markerUnits="strokeWidth">
              <path d="M 0 0 L 8 4 L 0 8 z" />
            </marker>
          </defs>

          {participants.map((participant, index) => {
            const x = participantX(index);
            return (
              <g key={participant.id}>
                <ParticipantHeader participant={participant} x={x} />
                <line className="sequence-lifeline" x1={x} y1="61" x2={x} y2={height - 20} />
              </g>
            );
          })}

          {sequence.messages.map((message, index) => {
            const fromIndex = participantIndexes.get(message.from);
            const toIndex = participantIndexes.get(message.to);
            if (fromIndex === undefined || toIndex === undefined) return null;

            const y = HEADER_HEIGHT + index * MESSAGE_HEIGHT + 28;
            return (
              <g key={`${index}-${message.from}-${message.to}`}>
                <circle className="sequence-step" cx="22" cy={y} r="11" />
                <text className="sequence-step-label" x="22" y={y + 3} textAnchor="middle">{index + 1}</text>
                <MessageArrow
                  fromX={participantX(fromIndex)}
                  toX={participantX(toIndex)}
                  y={y}
                  label={message.label}
                  kind={message.kind}
                  markerId={markerId}
                />
              </g>
            );
          })}
        </svg>
      </div>
    </section>
  );
}
