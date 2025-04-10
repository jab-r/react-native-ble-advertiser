#import "BLEAdvertiser.h"
@import CoreBluetooth;

@implementation BLEAdvertiser {
    BOOL centralReady;
    BOOL peripheralReady;
}

RCT_EXPORT_MODULE()

- (instancetype)init {
    if (self = [super init]) {
        dispatch_queue_t queue = dispatch_get_main_queue();
        self->centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:queue options:@{CBCentralManagerOptionShowPowerAlertKey: @(YES)}];
        self->peripheralManager = [[CBPeripheralManager alloc] initWithDelegate:self queue:queue options:nil];
        centralReady = NO;
        peripheralReady = NO;
    }
    return self;
}

- (dispatch_queue_t)methodQueue {
    return dispatch_get_main_queue();
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"onDeviceFound", @"onBTStatusChange"];
}

// Initialize the managers early and track state
RCT_EXPORT_METHOD(setCompanyId:(nonnull NSNumber *)companyId) {
    RCTLogInfo(@"setCompanyId called: %@", companyId);
    // Managers already initialized in init
}

RCT_EXPORT_METHOD(broadcast:(NSString *)uid serviceData:(NSString *)serviceData options:(NSDictionary *)options resolve:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    if (!peripheralReady) {
        reject(@"Peripheral Not Ready", @"Bluetooth Peripheral Manager is not ready yet.", nil);
        return;
    }

    CBUUID *serviceUUID = [CBUUID UUIDWithString:uid];
    NSMutableDictionary *advertisingData = [NSMutableDictionary dictionary];

    advertisingData[CBAdvertisementDataServiceUUIDsKey] = @[serviceUUID];

    if (serviceData && ![serviceData isEqualToString:@""]) {
        NSData *serviceDataBytes = [serviceData dataUsingEncoding:NSUTF8StringEncoding];
        advertisingData[CBAdvertisementDataServiceDataKey] = @{serviceUUID: serviceDataBytes};
    }

    if (!(options[@"includeDeviceName"] && [options[@"includeDeviceName"] boolValue])) {
        advertisingData[CBAdvertisementDataLocalNameKey] = @"";
    }

    [peripheralManager startAdvertising:advertisingData];
    resolve(@"Broadcasting");
}

RCT_EXPORT_METHOD(stopBroadcast:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    if (peripheralManager.isAdvertising) {
        [peripheralManager stopAdvertising];
    }
    resolve(@"Stopped broadcasting");
}

RCT_EXPORT_METHOD(scan:(NSArray *)payload options:(NSDictionary *)options resolve:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    if (!centralReady) {
        reject(@"Central Not Ready", @"Bluetooth Central Manager is not ready yet.", nil);
        return;
    }
    [centralManager scanForPeripheralsWithServices:nil options:@{CBCentralManagerScanOptionAllowDuplicatesKey:@YES}];
    resolve(@"Scanning");
}

RCT_EXPORT_METHOD(scanByService:(NSString *)uid options:(NSDictionary *)options resolve:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    if (!centralReady) {
        reject(@"Central Not Ready", @"Bluetooth Central Manager is not ready yet.", nil);
        return;
    }
    CBUUID *serviceUUID = [CBUUID UUIDWithString:uid];
    [centralManager scanForPeripheralsWithServices:@[serviceUUID] options:@{CBCentralManagerScanOptionAllowDuplicatesKey:@YES}];
    resolve(@"Scanning by service");
}

RCT_EXPORT_METHOD(stopScan:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    [centralManager stopScan];
    resolve(@"Stopped scanning");
}

RCT_EXPORT_METHOD(getAdapterState:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    resolve(centralReady ? @"STATE_ON" : @"STATE_OFF");
}

#pragma mark - CBCentralManagerDelegate

- (void)centralManagerDidUpdateState:(CBCentralManager *)central {
    centralReady = (central.state == CBManagerStatePoweredOn);
    NSDictionary *params = @{@"enabled": @(centralReady)};
    [self sendEventWithName:@"onBTStatusChange" body:params];
}

- (void)centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary<NSString *, id> *)advertisementData RSSI:(NSNumber *)RSSI {
    NSDictionary *params = @{
        @"deviceName": peripheral.name ?: @"",
        @"deviceAddress": peripheral.identifier.UUIDString ?: @"",
        @"rssi": RSSI ?: @(0),
        @"serviceUuids": [advertisementData[CBAdvertisementDataServiceUUIDsKey] valueForKey:@"UUIDString"] ?: @[],
        @"txPower": advertisementData[CBAdvertisementDataTxPowerLevelKey] ?: @(0)
    };
    [self sendEventWithName:@"onDeviceFound" body:params];
}

#pragma mark - CBPeripheralManagerDelegate

- (void)peripheralManagerDidUpdateState:(CBPeripheralManager *)peripheral {
    peripheralReady = (peripheral.state == CBManagerStatePoweredOn);
    // Optionally send an event or log the state change
}

@end