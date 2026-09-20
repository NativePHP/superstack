export type BarcodeFormat =
  | "qr"
  | "ean13"
  | "ean8"
  | "code128"
  | "code39"
  | "upca"
  | "upce"
  | "all";

export interface BridgeError extends Error {
  code?: string;
}

export interface ScanStartedResult {
  started: true;
}

export interface StopResult {
  stopped: boolean;
}

export interface ScannerCodeScannedPayload {
  data: string;
  format: string;
  id: string | null;
}

export interface ScannerCancelledPayload {
  reason: string | null;
  id: string | null;
}

export declare class PendingScan implements PromiseLike<
  ScanStartedResult | undefined
> {
  prompt(prompt: string): this;
  continuous(continuous?: boolean): this;
  gallery(allow?: boolean): this;
  formats(formats: BarcodeFormat[]): this;
  haptics(enabled?: boolean): this;
  zoom(ratio?: number): this;
  maxZoom(ratio?: number): this;
  zoomControl(enabled?: boolean): this;
  focusOnTap(enabled?: boolean): this;
  timeout(seconds?: number): this;
  id(id: string): this;
  getId(): string | null;
  then<TResult1 = ScanStartedResult | undefined, TResult2 = never>(
    onfulfilled?:
      | ((
          value: ScanStartedResult | undefined,
        ) => TResult1 | PromiseLike<TResult1>)
      | undefined
      | null,
    onrejected?:
      | ((reason: BridgeError) => TResult2 | PromiseLike<TResult2>)
      | undefined
      | null,
  ): PromiseLike<TResult1 | TResult2>;
}

export declare const Scanner: {
  scan(): PendingScan;
  stop(id?: string | null): Promise<StopResult>;
};

export declare const Events: {
  Scanner: {
    CodeScanned: "Sandip\\Scanner\\Native\\Events\\Scanner\\CodeScanned";
    Cancelled: "Sandip\\Scanner\\Native\\Events\\Scanner\\Cancelled";
  };
};

export declare function On(
  eventName: typeof Events.Scanner.CodeScanned,
  callback: (payload: ScannerCodeScannedPayload, eventName: string) => void,
): void;
export declare function On(
  eventName: typeof Events.Scanner.Cancelled,
  callback: (payload: ScannerCancelledPayload, eventName: string) => void,
): void;
export declare function On(
  eventName: string,
  callback: (payload: any, eventName: string) => void,
): void;

export declare function Off(
  eventName: typeof Events.Scanner.CodeScanned,
  callback: (payload: ScannerCodeScannedPayload, eventName: string) => void,
): void;
export declare function Off(
  eventName: typeof Events.Scanner.Cancelled,
  callback: (payload: ScannerCancelledPayload, eventName: string) => void,
): void;
export declare function Off(
  eventName: string,
  callback: (payload: any, eventName: string) => void,
): void;

declare const _default: {
  Scanner: typeof Scanner;
  On: typeof On;
  Off: typeof Off;
  Events: typeof Events;
  PendingScan: typeof PendingScan;
};

export default _default;
