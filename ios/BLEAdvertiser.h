#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <React/RCTLog.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <CoreLocation/CoreLocation.h>

@interface BLEAdvertiser : RCTEventEmitter <RCTBridgeModule, CBCentralManagerDelegate, CBPeripheralManagerDelegate, CBPeripheralDelegate, CLLocationManagerDelegate> {
    CBCentralManager *centralManager;
    CBPeripheralManager *peripheralManager;
    CLLocationManager *locationManager;
}

@end
  
