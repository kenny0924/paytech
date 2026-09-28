import { ApiError } from '../api/session.ts';

export const SAVE_WARNING_BYTES = 49_152;
export const SAVE_MAX_BYTES = 65_536;

export function utf8ByteLength(payload: string): number {
  return new TextEncoder().encode(payload).byteLength;
}

export function saveSizeState(payload: string): 'NORMAL' | 'WARNING' | 'TOO_LARGE' {
  const bytes = utf8ByteLength(payload);
  return bytes > SAVE_MAX_BYTES ? 'TOO_LARGE' : bytes >= SAVE_WARNING_BYTES ? 'WARNING' : 'NORMAL';
}

export function validateKey(releaseId: string, expectedVersion: number): string {
  return `risk-validate-v1-${releaseId}-${expectedVersion}`;
}

export interface PendingCommand {
  readonly path: string;
  readonly payload: string;
  readonly key: string;
}

export function randomCommand(path: string, payload: string, previous: PendingCommand | null): PendingCommand {
  if (previous !== null && previous.path === path && previous.payload === payload) return previous;
  return Object.freeze({ path, payload, key: crypto.randomUUID() });
}

export type CommandSettlement = 'SUCCESS' | 'DETERMINISTIC_HTTP_FAILURE' | 'UNKNOWN_RESULT';
export type CommandFailureSettlement = Exclude<CommandSettlement, 'SUCCESS'>;

const deterministicFailureStatuses = new Set([400, 401, 403, 404, 409, 413, 422, 429]);

/** Use directly in a command catch; arbitrary errors with a status-like property are not HTTP evidence. */
export function commandFailureSettlement(error: unknown): CommandFailureSettlement {
  return error instanceof ApiError && deterministicFailureStatuses.has(error.status)
    ? 'DETERMINISTIC_HTTP_FAILURE' : 'UNKNOWN_RESULT';
}

export function settlePendingCommand(pending: PendingCommand, settlement: CommandSettlement): PendingCommand | null {
  return settlement === 'SUCCESS' || settlement === 'DETERMINISTIC_HTTP_FAILURE' ? null : pending;
}
