export as namespace BLEAdvertiser;

export interface ScanOptions {
    numberOfMatches?: number;
    matchMode?: number;
    scanMode?: number;
    reportDelay?: number;
}

export interface BroadcastOptions {
    txPowerLevel?: number;
    advertiseMode?: number;
    includeDeviceName?: boolean;
    includeTxPowerLevel?: boolean;
    connectable?: boolean;
}

export interface BeaconOptions {
    major?: number;
    minor?: number;
    measuredPower?: number;
    identifier?: string;
}

export interface RegionOptions {
    major?: number;
    minor?: number;
    identifier?: string;
}

export interface RegionResponse {
    message: string;
    identifier: string;
}

export interface RegionInfo {
    identifier: string;
    uuid: string;
    major?: number;
    minor?: number;
}

export interface BeaconData {
    uuid: string;
    major: number;
    minor: number;
    measuredPower?: number;
    rssi?: number;
    accuracy?: number;
    proximity?: string;
}

export interface DeviceFoundEvent {
    deviceName: string;
    deviceAddress: string;
    rssi: number;
    serviceUuids: string[];
    txPower: number;
    serviceData: Record<string, string>;
    beaconData?: {
        uuid: string;
        major: number;
        minor: number;
        measuredPower: number;
        isBeacon: boolean;
    };
    distance?: number;
}

export interface BeaconDiscoveredEvent {
    identifier: string;
    beacons: BeaconData[];
}

export interface RegionEvent {
    identifier: string;
    uuid: string;
    major?: number;
    minor?: number;
    state?: string;
}

// Core methods
export function setCompanyId(companyId: number): void;
export function broadcast(uuid: string, serviceData?: string): Promise<string>;
export function broadcastAsBeacon(uuid: string, options?: BeaconOptions): Promise<string>;
export function stopBroadcast(): Promise<string>;
export function scan(manufDataFilter: number[], options?: ScanOptions): Promise<string>;
export function scanByService(uidFilter: string, options?: ScanOptions): Promise<string>;
export function stopScan(): Promise<string>;
export function enableAdapter(): void;
export function disableAdapter(): void;
export function getAdapterState(): Promise<string>;
export function isActive(): Promise<boolean>;
export function scanForIBeacons(uuid: string, options?: ScanOptions): Promise<string>;

// iBeacon monitoring methods
export function startMonitoringForRegion(uuid: string, options?: RegionOptions): Promise<RegionResponse>;
export function stopMonitoringForRegion(identifier: string): Promise<RegionResponse>;
export function getMonitoredRegions(): Promise<RegionInfo[]>;

// iBeacon ranging methods
export function startRangingBeaconsInRegion(uuid: string, options?: RegionOptions): Promise<RegionResponse>;
export function stopRangingBeaconsInRegion(identifier: string): Promise<RegionResponse>;
export function getRangedRegions(): Promise<RegionInfo[]>;