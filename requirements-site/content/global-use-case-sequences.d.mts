export type SequenceParticipantType = "actor" | "gateway" | "entry" | "core" | "external";
export type SequenceMessageKind = "sync" | "async";

export interface SequenceParticipant {
  name: string;
  type: SequenceParticipantType;
}

export interface SequenceMessage {
  from: string;
  to: string;
  label: string;
  kind: SequenceMessageKind;
}

export interface GlobalUseCaseSequence {
  summary: string;
  participants: string[];
  messages: SequenceMessage[];
}

export const SEQUENCE_PARTICIPANTS: Record<string, SequenceParticipant>;
export const GLOBAL_USE_CASE_SEQUENCES: Record<string, GlobalUseCaseSequence>;
