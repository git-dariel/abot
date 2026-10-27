export declare const PROTOCOL_VERSION: "1.0";

export type TransferState =
  | "IDLE"
  | "FILE_SELECTED"
  | "GESTURE_ARMED"
  | "TRANSFER_OFFERED"
  | "AWAITING_REMOTE_CONFIRMATION"
  | "ACCEPTED"
  | "TRANSFERRING"
  | "VERIFYING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type MessageType =
  | "transfer.offer"
  | "transfer.accept"
  | "transfer.reject"
  | "transfer.cancel"
  | "transfer.progress"
  | "transfer.complete"
  | "transfer.failed";

export interface FileMetadata {
  id: string;
  name: string;
  size: number;
  sha256: string;
  mediaType?: string;
}

export interface ProtocolEnvelope<TPayload = unknown> {
  protocolVersion: "1.0";
  messageId: string;
  transferId: string;
  sentAt: string;
  type: MessageType;
  payload: TPayload;
}

export declare const transferStates: readonly TransferState[];
export declare const messageTypes: readonly MessageType[];
export declare function isSafeLeafName(value: string): boolean;
