#import "BLEAdvertiser.h"
#import <CoreBluetooth/CoreBluetooth.h>
#import <CoreLocation/CoreLocation.h>

@implementation BLEAdvertiser {
    BOOL centralReady;
    BOOL peripheralReady;
    BOOL locationReady;
    NSMutableDictionary *monitoredRegions;
    NSMutableDictionary *rangedRegions;
}

RCT_EXPORT_MODULE()

- (instancetype)init {
    if (self = [super init]) {
        dispatch_queue_t queue = dispatch_get_main_queue();
        self->centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:queue options:@{CBCentralManagerOptionShowPowerAlertKey: @(YES)}];
        self->peripheralManager = [[CBPeripheralManager alloc] initWithDelegate:self queue:queue options:nil];
        self->locationManager = [[CLLocationManager alloc] init];
        self->locationManager.delegate = self;
        
        centralReady = NO;
        peripheralReady = NO;
        locationReady = NO;
        
        monitoredRegions = [NSMutableDictionary dictionary];
        rangedRegions = [NSMutableDictionary dictionary];
        // Request location authorization for iBeacon monitoring
        // Since we require iOS 15+, we can use the newer APIs directly
        CLAuthorizationStatus status = self->locationManager.authorizationStatus;
        if (status == kCLAuthorizationStatusNotDetermined) {
            [self->locationManager requestAlwaysAuthorization];
        }
    }
    return self;
}

- (dispatch_queue_t)methodQueue {
    return dispatch_get_main_queue();
}

- (NSArray<NSString *> *)supportedEvents {
    return @[
        @"onDeviceFound",
        @"onBTStatusChange",
        @"onBeaconDiscovered",
        @"onRegionEnter",
        @"onRegionExit",
        @"onLocationAuthorizationChange"
    ];
}

// Initialize the managers early and track state
RCT_EXPORT_METHOD(setCompanyId:(nonnull NSNumber *)companyId) {
    RCTLogInfo(@"setCompanyId called: %@", companyId);
    // Managers already initialized in init
}

RCT_EXPORT_METHOD(broadcast:(NSString *)uid
                  serviceData:(NSString *)serviceData 
                  resolve:(RCTPromiseResolveBlock)resolve 
                  rejecter:(RCTPromiseRejectBlock)reject) 
{
    if (!peripheralReady) {
        reject(@"PeripheralNotReady", @"Bluetooth Peripheral Manager is not ready yet.", nil);
        return;
    }

    CBUUID *serviceUUID = [CBUUID UUIDWithString:uid];

    NSMutableDictionary *advertisingData = [NSMutableDictionary dictionary];
    advertisingData[CBAdvertisementDataServiceUUIDsKey] = @[serviceUUID];
    
     if (serviceData && [serviceData length] != 0) {
        NSData *serviceDataBytes = [serviceData dataUsingEncoding:NSUTF8StringEncoding];

        // Check data size: BLE advertising payload should be small (recommended <20 bytes)
        if ([serviceDataBytes length] > 20) {
            reject(@"ServiceDataTooLarge", @"ServiceData exceeds BLE recommended size limit (20 bytes).", nil);
            return;
        }
        advertisingData[CBAdvertisementDataServiceDataKey] = @{serviceUUID: serviceDataBytes};
    }
    
    if (peripheralManager.isAdvertising) {
        [peripheralManager stopAdvertising];
    }

    @try {
        [peripheralManager startAdvertising:advertisingData];
        resolve(@"Broadcasting started successfully");
    }
    @catch (NSException *exception) {
        reject(@"StartAdvertisingFailed", exception.reason, nil);
    }
}

// New method to broadcast as iBeacon using CoreLocation
RCT_EXPORT_METHOD(broadcastAsBeacon:(NSString *)deviceId
                  options:(NSDictionary *)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    if (!peripheralReady) {
        reject(@"PeripheralNotReady", @"Bluetooth Peripheral Manager is not ready yet.", nil);
        return;
    }

    // Parse UUID for creating the iBeacon
    NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:deviceId];
    if (!uuid) {
        reject(@"InvalidUUID", @"DeviceId is not a valid UUID string.", nil);
        return;
    }
    
    // Define major and minor values
    uint16_t major = 1; // Default major value
    uint16_t minor = 1; // Default minor value
    
    // Check if major/minor are provided in options
    if (options[@"major"]) {
        major = [options[@"major"] unsignedShortValue];
    }
    
    if (options[@"minor"]) {
        minor = [options[@"minor"] unsignedShortValue];
    }
    
    // Create a CLBeaconRegion
    CLBeaconRegion *beaconRegion = [[CLBeaconRegion alloc]
                                   initWithUUID:uuid
                                   major:major
                                   minor:minor
                                   identifier:[NSString stringWithFormat:@"%@-%d-%d", [uuid UUIDString], major, minor]];
    
    // Get the beacon peripheral data
    NSDictionary *peripheralData = [beaconRegion peripheralDataWithMeasuredPower:options[@"measuredPower"]];
    
    if (peripheralManager.isAdvertising) {
        [peripheralManager stopAdvertising];
    }
    
    @try {
        [peripheralManager startAdvertising:peripheralData];
        resolve(@"Broadcasting as iBeacon started successfully");
    }
    @catch (NSException *exception) {
        reject(@"StartAdvertisingFailed", exception.reason, nil);
    }
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
RCT_EXPORT_METHOD(scanForIBeacons:(NSString *)uuid
                   options:(NSDictionary *)options
                   resolve:(RCTPromiseResolveBlock)resolve
                   rejecter:(RCTPromiseRejectBlock)reject) {
    if (!centralReady) {
        reject(@"Central Not Ready", @"Bluetooth Central Manager is not ready yet.", nil);
        return;
    }
    
    // Scan for all devices to catch manufacturer data
    [centralManager scanForPeripheralsWithServices:nil options:@{CBCentralManagerScanOptionAllowDuplicatesKey:@YES}];
    resolve(@"Scanning for iBeacons");
}

// New method to monitor iBeacon regions using CoreLocation
RCT_EXPORT_METHOD(startMonitoringForRegion:(NSString *)uuid
                  options:(NSDictionary *)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    // Since we require iOS 15+, we can use the newer APIs directly
    CLAuthorizationStatus status = self->locationManager.authorizationStatus;
    BOOL isAuthorized = (status == kCLAuthorizationStatusAuthorizedAlways ||
                        status == kCLAuthorizationStatusAuthorizedWhenInUse);
    
    if (!isAuthorized) {
        reject(@"LocationPermissionDenied", @"Location permission is required for iBeacon monitoring", nil);
        return;
    }
    
    // Parse UUID
    NSUUID *proximityUUID = [[NSUUID alloc] initWithUUIDString:uuid];
    if (!proximityUUID) {
        reject(@"InvalidUUID", @"UUID is not valid", nil);
        return;
    }
    
    // Create region identifier
    NSString *identifier = options[@"identifier"] ?: [uuid copy];
    
    // Create the beacon region
    CLBeaconRegion *beaconRegion;
    
    if (options[@"major"] && options[@"minor"]) {
        // Monitor for specific major and minor
        beaconRegion = [[CLBeaconRegion alloc]
                        initWithUUID:proximityUUID
                        major:[options[@"major"] unsignedShortValue]
                        minor:[options[@"minor"] unsignedShortValue]
                        identifier:identifier];
    } else if (options[@"major"]) {
        // Monitor for specific major only
        beaconRegion = [[CLBeaconRegion alloc]
                        initWithUUID:proximityUUID
                        major:[options[@"major"] unsignedShortValue]
                        identifier:identifier];
    } else {
        // Monitor for all beacons with this UUID
        beaconRegion = [[CLBeaconRegion alloc]
                        initWithUUID:proximityUUID
                        identifier:identifier];
    }
    
    // Configure region options
    beaconRegion.notifyEntryStateOnDisplay = YES;
    beaconRegion.notifyOnEntry = YES;
    beaconRegion.notifyOnExit = YES;
    
    // Store the region for later reference
    monitoredRegions[identifier] = beaconRegion;
    
    // Start monitoring
    [locationManager startMonitoringForRegion:beaconRegion];
    
    resolve(@{
        @"message": @"Started monitoring region",
        @"identifier": identifier
    });
}

// New method to start ranging iBeacons
RCT_EXPORT_METHOD(startRangingBeaconsInRegion:(NSString *)uuid
                  options:(NSDictionary *)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    // Since we require iOS 15+, we can use the newer APIs directly
    CLAuthorizationStatus status = self->locationManager.authorizationStatus;
    BOOL isAuthorized = (status == kCLAuthorizationStatusAuthorizedAlways ||
                        status == kCLAuthorizationStatusAuthorizedWhenInUse);
    
    if (!isAuthorized) {
        reject(@"LocationPermissionDenied", @"Location permission is required for iBeacon ranging", nil);
        return;
    }
    
    // Parse UUID
    NSUUID *proximityUUID = [[NSUUID alloc] initWithUUIDString:uuid];
    if (!proximityUUID) {
        reject(@"InvalidUUID", @"UUID is not valid", nil);
        return;
    }
    
    // Create region identifier
    NSString *identifier = options[@"identifier"] ?: [uuid copy];
    
    // Create the beacon region
    CLBeaconRegion *beaconRegion;
    
    if (options[@"major"] && options[@"minor"]) {
        // Range for specific major and minor
        beaconRegion = [[CLBeaconRegion alloc]
                        initWithUUID:proximityUUID
                        major:[options[@"major"] unsignedShortValue]
                        minor:[options[@"minor"] unsignedShortValue]
                        identifier:identifier];
    } else if (options[@"major"]) {
        // Range for specific major only
        beaconRegion = [[CLBeaconRegion alloc]
                        initWithUUID:proximityUUID
                        major:[options[@"major"] unsignedShortValue]
                        identifier:identifier];
    } else {
        // Range for all beacons with this UUID
        beaconRegion = [[CLBeaconRegion alloc]
                        initWithUUID:proximityUUID
                        identifier:identifier];
    }
    
    // Store the region for later reference
    rangedRegions[identifier] = beaconRegion;
    // Start ranging
    // Since we require iOS 15+, we can use the newer APIs directly
    CLBeaconIdentityConstraint *constraint = [[CLBeaconIdentityConstraint alloc]
                                             initWithUUID:beaconRegion.UUID
                                             major:[beaconRegion.major intValue]
                                             minor:[beaconRegion.minor intValue]];
    [locationManager startRangingBeaconsSatisfyingConstraint:constraint];
    
    resolve(@{
        @"message": @"Started ranging beacons",
        @"identifier": identifier
    });
}

// Stop monitoring a specific region
RCT_EXPORT_METHOD(stopMonitoringForRegion:(NSString *)identifier
                  resolve:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    CLBeaconRegion *region = monitoredRegions[identifier];
    
    if (region) {
        [locationManager stopMonitoringForRegion:region];
        [monitoredRegions removeObjectForKey:identifier];
        resolve(@{@"message": @"Stopped monitoring region", @"identifier": identifier});
    } else {
        reject(@"RegionNotFound", @"No monitored region with that identifier", nil);
    }
}

// Stop ranging beacons in a specific region
RCT_EXPORT_METHOD(stopRangingBeaconsInRegion:(NSString *)identifier
                  resolve:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    CLBeaconRegion *region = rangedRegions[identifier];
    if (region) {
        // Since we require iOS 15+, we can use the newer APIs directly
        NSUUID *uuid = region.UUID;
        NSNumber *major = region.major;
        NSNumber *minor = region.minor;
        
        CLBeaconIdentityConstraint *constraint;
        if (major != nil && minor != nil) {
            constraint = [[CLBeaconIdentityConstraint alloc] initWithUUID:uuid major:[major intValue] minor:[minor intValue]];
        } else if (major != nil) {
            constraint = [[CLBeaconIdentityConstraint alloc] initWithUUID:uuid major:[major intValue]];
        } else {
            constraint = [[CLBeaconIdentityConstraint alloc] initWithUUID:uuid];
        }
        
        [locationManager stopRangingBeaconsSatisfyingConstraint:constraint];
        
        [rangedRegions removeObjectForKey:identifier];
        resolve(@{@"message": @"Stopped ranging beacons", @"identifier": identifier});
    } else {
        reject(@"RegionNotFound", @"No ranged region with that identifier", nil);
    }
}

// Get all monitored regions
RCT_EXPORT_METHOD(getMonitoredRegions:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    NSMutableArray *regions = [NSMutableArray array];
    
    for (NSString *identifier in monitoredRegions) {
        CLBeaconRegion *region = monitoredRegions[identifier];
        [regions addObject:@{
            @"identifier": region.identifier,
            @"uuid": [region.UUID UUIDString],
            @"major": region.major ? @([region.major unsignedShortValue]) : [NSNull null],
            @"minor": region.minor ? @([region.minor unsignedShortValue]) : [NSNull null]
        }];
    }
    
    resolve(regions);
}

// Get all ranged regions
RCT_EXPORT_METHOD(getRangedRegions:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    NSMutableArray *regions = [NSMutableArray array];
    
    for (NSString *identifier in rangedRegions) {
        CLBeaconRegion *region = rangedRegions[identifier];
        [regions addObject:@{
            @"identifier": region.identifier,
            @"uuid": [region.UUID UUIDString],
            @"major": region.major ? @([region.major unsignedShortValue]) : [NSNull null],
            @"minor": region.minor ? @([region.minor unsignedShortValue]) : [NSNull null]
        }];
    }
    
    resolve(regions);
}

// Modify the didDiscoverPeripheral method to also detect iBeacons
- (void)centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary<NSString *, id> *)advertisementData RSSI:(NSNumber *)RSSI {
    // Extract service data (existing code)
    NSMutableDictionary *serviceDataDict = [NSMutableDictionary dictionary];
    NSDictionary *rawServiceData = advertisementData[CBAdvertisementDataServiceDataKey];
    
    if (rawServiceData) {
        for (CBUUID *serviceUUID in rawServiceData) {
            NSData *data = rawServiceData[serviceUUID];
            if (data) {
                // Convert NSData to string
                NSString *dataString = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
                if (dataString) {
                    serviceDataDict[[serviceUUID UUIDString]] = dataString;
                }
            }
        }
    }
    
    // Extract manufacturer data to check for iBeacons
    NSData *manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey];
    NSMutableDictionary *beaconData = [NSMutableDictionary dictionary];
    
    if (manufacturerData && [manufacturerData length] >= 23) { // Minimum size for iBeacon data
        const unsigned char *bytes = [manufacturerData bytes];
        
        // Check for Apple's company ID (0x004C) and iBeacon type (0x02) and length (0x15)
        if (bytes[0] == 0x4C && bytes[1] == 0x00 && bytes[2] == 0x02 && bytes[3] == 0x15) {
            // Extract UUID (16 bytes)
            NSData *uuidData = [NSData dataWithBytes:&bytes[4] length:16];
            NSUUID *proximityUUID = [[NSUUID alloc] initWithUUIDBytes:[uuidData bytes]];
            // Extract major (2 bytes)
            uint16_t major = (uint16_t)(bytes[20] << 8 | bytes[21]);
            major = CFSwapInt16BigToHost(major);
            
            // Extract minor (2 bytes)
            uint16_t minor = (uint16_t)(bytes[22] << 8 | bytes[23]);
            minor = CFSwapInt16BigToHost(minor);
            
            // Extract measured power (1 byte)
            int8_t measuredPower = (int8_t)bytes[24];
            
            // Add beacon data to dictionary
            beaconData[@"uuid"] = [proximityUUID UUIDString];
            beaconData[@"major"] = @(major);
            beaconData[@"minor"] = @(minor);
            beaconData[@"measuredPower"] = @(measuredPower);
            beaconData[@"isBeacon"] = @YES;
        }
    }
    
    // Create the event parameters, now including beacon data if available
    NSMutableDictionary *params = [NSMutableDictionary dictionaryWithDictionary:@{
        @"deviceName": peripheral.name ?: @"",
        @"deviceAddress": peripheral.identifier.UUIDString ?: @"",
        @"rssi": RSSI ?: @(0),
        @"serviceUuids": [advertisementData[CBAdvertisementDataServiceUUIDsKey] valueForKey:@"UUIDString"] ?: @[],
        @"txPower": advertisementData[CBAdvertisementDataTxPowerLevelKey] ?: @(0),
        @"serviceData": serviceDataDict
    }];
    
    // Add beacon data if we found an iBeacon
    if (beaconData.count > 0) {
        params[@"beaconData"] = beaconData;
    }
    
    // Calculate approximate distance based on RSSI and measured power if available
    if (beaconData[@"measuredPower"]) {
        double measuredPower = [beaconData[@"measuredPower"] doubleValue];
        double rssi = [RSSI doubleValue];
        double ratio = rssi / measuredPower;
        
        double distance;
        if (ratio < 1.0) {
            distance = pow(ratio, 10);
        } else {
            distance = (0.89976) * pow(ratio, 7.7095) + 0.111;  
        }
        
        params[@"distance"] = @(distance);
    }
    
    [self sendEventWithName:@"onDeviceFound" body:params];
}
#pragma mark - CBCentralManagerDelegate

- (void)centralManagerDidUpdateState:(CBCentralManager *)central {
    centralReady = (central.state == CBManagerStatePoweredOn);
    NSDictionary *params = @{@"enabled": @(centralReady)};
    [self sendEventWithName:@"onBTStatusChange" body:params];
}
#pragma mark - CBPeripheralManagerDelegate
#pragma mark - CBPeripheralManagerDelegate

- (void)peripheralManagerDidUpdateState:(CBPeripheralManager *)peripheral {
    peripheralReady = (peripheral.state == CBManagerStatePoweredOn);
    // Optionally send an event or log the state change
}

#pragma mark - CLLocationManagerDelegate

// Handle location authorization changes
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-implementations"
- (void)locationManager:(CLLocationManager *)manager didChangeAuthorizationStatus:(CLAuthorizationStatus)status {
    BOOL authorized = (status == kCLAuthorizationStatusAuthorizedAlways ||
                      status == kCLAuthorizationStatusAuthorizedWhenInUse);
    
    [self handleAuthorizationChange:authorized];
}
#pragma clang diagnostic pop

// iOS 14+ authorization delegate method
- (void)locationManagerDidChangeAuthorization:(CLLocationManager *)manager API_AVAILABLE(ios(14.0)) {
    BOOL authorized = (manager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways ||
                      manager.authorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse);
    
    [self handleAuthorizationChange:authorized];
}

// Common handler for authorization changes
- (void)handleAuthorizationChange:(BOOL)authorized {
    locationReady = authorized;
    [self sendEventWithName:@"onLocationAuthorizationChange" body:@{
        @"authorized": @(authorized)
    }];
}


// Handle region monitoring events
- (void)locationManager:(CLLocationManager *)manager didEnterRegion:(CLRegion *)region {
    if ([region isKindOfClass:[CLBeaconRegion class]]) {
        CLBeaconRegion *beaconRegion = (CLBeaconRegion *)region;
        
        [self sendEventWithName:@"onRegionEnter" body:@{
            @"identifier": beaconRegion.identifier,
            @"uuid": [beaconRegion.UUID UUIDString],
            @"major": beaconRegion.major ? @([beaconRegion.major unsignedShortValue]) : [NSNull null],
            @"minor": beaconRegion.minor ? @([beaconRegion.minor unsignedShortValue]) : [NSNull null]
        }];
    }
}

- (void)locationManager:(CLLocationManager *)manager didExitRegion:(CLRegion *)region {
    if ([region isKindOfClass:[CLBeaconRegion class]]) {
        CLBeaconRegion *beaconRegion = (CLBeaconRegion *)region;
        
        [self sendEventWithName:@"onRegionExit" body:@{
            @"identifier": beaconRegion.identifier,
            @"uuid": [beaconRegion.UUID UUIDString],
            @"major": beaconRegion.major ? @([beaconRegion.major unsignedShortValue]) : [NSNull null],
            @"minor": beaconRegion.minor ? @([beaconRegion.minor unsignedShortValue]) : [NSNull null]
        }];
    }
}

- (void)locationManager:(CLLocationManager *)manager didDetermineState:(CLRegionState)state forRegion:(CLRegion *)region {
    if ([region isKindOfClass:[CLBeaconRegion class]]) {
        CLBeaconRegion *beaconRegion = (CLBeaconRegion *)region;
        
        NSString *stateString;
        switch (state) {
            case CLRegionStateInside:
                stateString = @"inside";
                break;
            case CLRegionStateOutside:
                stateString = @"outside";
                break;
            case CLRegionStateUnknown:
                stateString = @"unknown";
                break;
        }
        
        [self sendEventWithName:@"onRegionStateChange" body:@{
            @"identifier": beaconRegion.identifier,
            @"uuid": [beaconRegion.UUID UUIDString],
            @"major": beaconRegion.major ? @([beaconRegion.major unsignedShortValue]) : [NSNull null],
            @"minor": beaconRegion.minor ? @([beaconRegion.minor unsignedShortValue]) : [NSNull null],
            @"state": stateString
        }];
    }
}

// Handle beacon ranging
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-implementations"
- (void)locationManager:(CLLocationManager *)manager didRangeBeacons:(NSArray<CLBeacon *> *)beacons inRegion:(CLBeaconRegion *)region {
    [self processRangedBeacons:beacons region:region.identifier];
}
#pragma clang diagnostic pop

// iOS 13+ beacon ranging delegate method
- (void)locationManager:(CLLocationManager *)manager didRangeBeacons:(NSArray<CLBeacon *> *)beacons satisfyingConstraint:(CLBeaconIdentityConstraint *)constraint API_AVAILABLE(ios(13.0)) {
    // Find the region identifier from our stored regions
    NSString *identifier = nil;
    for (NSString *key in rangedRegions) {
        CLBeaconRegion *region = rangedRegions[key];
        if ([region.UUID isEqual:constraint.UUID]) {
            if ((constraint.major == nil && region.major == nil) ||
                (constraint.major != nil && region.major != nil && [constraint.major isEqual:region.major])) {
                if ((constraint.minor == nil && region.minor == nil) ||
                    (constraint.minor != nil && region.minor != nil && [constraint.minor isEqual:region.minor])) {
                    identifier = key;
                    break;
                }
            }
        }
    }
    
    if (identifier) {
        [self processRangedBeacons:beacons region:identifier];
    }
}

// Common method to process ranged beacons
- (void)processRangedBeacons:(NSArray<CLBeacon *> *)beacons region:(NSString *)regionIdentifier {
    NSMutableArray *beaconArray = [NSMutableArray array];
    
    for (CLBeacon *beacon in beacons) {
        // Calculate proximity string
        NSString *proximityString;
        switch (beacon.proximity) {
            case CLProximityImmediate:
                proximityString = @"immediate";
                break;
            case CLProximityNear:
                proximityString = @"near";
                break;
            case CLProximityFar:
                proximityString = @"far";
                break;
            case CLProximityUnknown:
            default:
                proximityString = @"unknown";
                break;
        }
        
        [beaconArray addObject:@{
            @"uuid": [beacon.UUID UUIDString],
            @"major": @(beacon.major.unsignedShortValue),
            @"minor": @(beacon.minor.unsignedShortValue),
            @"rssi": @(beacon.rssi),
            @"accuracy": @(beacon.accuracy),
            @"proximity": proximityString
        }];
    }
    [self sendEventWithName:@"onBeaconDiscovered" body:@{
        @"identifier": regionIdentifier,
        @"beacons": beaconArray
    }];
}

// Handle region monitoring errors
- (void)locationManager:(CLLocationManager *)manager monitoringDidFailForRegion:(CLRegion *)region withError:(NSError *)error {
    if ([region isKindOfClass:[CLBeaconRegion class]]) {
        CLBeaconRegion *beaconRegion = (CLBeaconRegion *)region;
        
        NSLog(@"Monitoring failed for region %@: %@", beaconRegion.identifier, error);
    }
}

// Handle ranging errors
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-implementations"
- (void)locationManager:(CLLocationManager *)manager rangingBeaconsDidFailForRegion:(CLBeaconRegion *)region withError:(NSError *)error {
    NSLog(@"Ranging failed for region %@: %@", region.identifier, error);
}
#pragma clang diagnostic pop

// iOS 13+ ranging error delegate method
- (void)locationManager:(CLLocationManager *)manager didFailRangingBeaconsForConstraint:(CLBeaconIdentityConstraint *)constraint error:(NSError *)error API_AVAILABLE(ios(13.0)) {
    NSLog(@"Ranging failed for constraint %@: %@", constraint, error);
}

@end