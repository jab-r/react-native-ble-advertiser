package com.jabresearch.bleadvertiser;

import com.facebook.react.uimanager.*;
import com.facebook.react.bridge.*;
import com.facebook.systrace.Systrace;
import com.facebook.systrace.SystraceMessage;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactRootView;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.os.Build;
import android.location.Location;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.lang.Thread;
import java.lang.Object;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;
import java.nio.ByteBuffer;
import java.util.Set;

public class BLEAdvertiserModule extends ReactContextBaseJavaModule {

    public static final String TAG = "BleAdvertiser";
    private BluetoothAdapter mBluetoothAdapter;
    
    private static Hashtable<String, BluetoothLeAdvertiser> mAdvertiserList;
    private static Hashtable<String, AdvertiseCallback> mAdvertiserCallbackList;
    private static BluetoothLeScanner mScanner;
    private static ScanCallback mScannerCallback;
    private int companyId;
    private Boolean mObservedState;
    
    // iBeacon related fields
    private static final int APPLE_MANUFACTURER_ID = 0x004C;
    private static final byte IBEACON_TYPE = 0x02;
    private static final byte IBEACON_TYPE_LENGTH = 0x15;
    
    // Maps to track monitored and ranged regions
    private Map<String, BeaconRegion> monitoredRegions;
    private Map<String, BeaconRegion> rangedRegions;
    
    // Class to represent a beacon region
    private class BeaconRegion {
        String identifier;
        UUID uuid;
        Integer major;
        Integer minor;
        
        BeaconRegion(String identifier, UUID uuid, Integer major, Integer minor) {
            this.identifier = identifier;
            this.uuid = uuid;
            this.major = major;
            this.minor = minor;
        }
        
        @Override
        public String toString() {
            return "BeaconRegion{" +
                   "identifier='" + identifier + '\'' +
                   ", uuid=" + uuid +
                   ", major=" + major +
                   ", minor=" + minor +
                   '}';
        }
    }

    //Constructor
    public BLEAdvertiserModule(ReactApplicationContext reactContext) {
        super(reactContext);

        mAdvertiserList = new Hashtable<String, BluetoothLeAdvertiser>();
        mAdvertiserCallbackList = new Hashtable<String, AdvertiseCallback>();
        
        // Initialize beacon region maps
        monitoredRegions = new HashMap<>();
        rangedRegions = new HashMap<>();

        BluetoothManager bluetoothManager = (BluetoothManager) reactContext.getApplicationContext()
                .getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (mBluetoothAdapter != null) {
            mObservedState = mBluetoothAdapter.isEnabled();
        }

        this.companyId = 0x0000;

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        reactContext.registerReceiver(mReceiver, filter);
    }
    
    @Override
    public String getName() {
        return "BLEAdvertiser";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("ADVERTISE_MODE_BALANCED",        AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        constants.put("ADVERTISE_MODE_LOW_LATENCY",     AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        constants.put("ADVERTISE_MODE_LOW_POWER",       AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
        constants.put("ADVERTISE_TX_POWER_HIGH",        AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        constants.put("ADVERTISE_TX_POWER_LOW",         AdvertiseSettings.ADVERTISE_TX_POWER_LOW);
        constants.put("ADVERTISE_TX_POWER_MEDIUM",      AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        constants.put("ADVERTISE_TX_POWER_ULTRA_LOW",   AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW);

        constants.put("SCAN_MODE_BALANCED",             ScanSettings.SCAN_MODE_BALANCED);
        constants.put("SCAN_MODE_LOW_LATENCY",          ScanSettings.SCAN_MODE_LOW_LATENCY);
        constants.put("SCAN_MODE_LOW_POWER",            ScanSettings.SCAN_MODE_LOW_POWER);
        constants.put("SCAN_MODE_OPPORTUNISTIC",        ScanSettings.SCAN_MODE_OPPORTUNISTIC);
        constants.put("MATCH_MODE_AGGRESSIVE",          ScanSettings.MATCH_MODE_AGGRESSIVE);
        constants.put("MATCH_MODE_STICKY",              ScanSettings.MATCH_MODE_STICKY);
        constants.put("MATCH_NUM_FEW_ADVERTISEMENT",    ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT);
        constants.put("MATCH_NUM_MAX_ADVERTISEMENT",    ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT);
        constants.put("MATCH_NUM_ONE_ADVERTISEMENT",    ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT);

        return constants;
    }

    @ReactMethod
    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    @ReactMethod
    public void broadcast(String uid, String serviceData, ReadableMap options, Promise promise) {
        if (mBluetoothAdapter == null) {
            Log.w("BLEAdvertiserModule", "Device does not support Bluetooth. Adapter is Null");
            promise.reject("Device does not support Bluetooth. Adapter is Null");
            return;
        }
        
        if (mBluetoothAdapter == null) {
            Log.w("BLEAdvertiserModule", "mBluetoothAdapter unavailable");
            promise.reject("mBluetoothAdapter unavailable");
            return;
        }

        if (mObservedState != null && !mObservedState) {
            Log.w("BLEAdvertiserModule", "Bluetooth disabled");
            promise.reject("Bluetooth disabled");
            return;
        }

        BluetoothLeAdvertiser tempAdvertiser;
        AdvertiseCallback tempCallback;

        if (mAdvertiserList.containsKey(uid)) {
            tempAdvertiser = mAdvertiserList.remove(uid);
            tempCallback = mAdvertiserCallbackList.remove(uid);

            tempAdvertiser.stopAdvertising(tempCallback);
        } else {
            tempAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
            tempCallback = new BLEAdvertiserModule.SimpleAdvertiseCallback(promise);
        }
         
        if (tempAdvertiser == null) {
            Log.w("BLEAdvertiserModule", "Advertiser Not Available unavailable");
            promise.reject("Advertiser unavailable on this device");
            return;
        }
        
        Log.d(TAG, "Broadcasting with UUID: " + uid + " and service data: " + serviceData);
        
        AdvertiseSettings settings = buildAdvertiseSettings(options);
        AdvertiseData data = buildAdvertiseData(ParcelUuid.fromString(uid), serviceData, options);

        tempAdvertiser.startAdvertising(settings, data, tempCallback);

        mAdvertiserList.put(uid, tempAdvertiser);
        mAdvertiserCallbackList.put(uid, tempCallback);
    }

    private byte[] toByteArray(ReadableArray payload) {
        byte[] temp = new byte[payload.size()];
        for (int i = 0; i < payload.size(); i++) {
            temp[i] = (byte)payload.getInt(i);
        }
        return temp;
    }

    private WritableArray toByteArray(byte[] payload) {
        WritableArray array = Arguments.createArray();
        for (byte data : payload) {
            array.pushInt(data);
        }
        return array;
    }

   @ReactMethod
    public void stopBroadcast(final Promise promise) {
        Log.w("BLEAdvertiserModule", "Stop Broadcast call");

        if (mBluetoothAdapter == null) {
            Log.w("BLEAdvertiserModule", "mBluetoothAdapter unavailable");
            promise.reject("mBluetoothAdapter unavailable");
            return;
        } 

        if (mObservedState != null && !mObservedState) {
            Log.w("BLEAdvertiserModule", "Bluetooth disabled");
            promise.reject("Bluetooth disabled");
            return;
        }

        WritableArray promiseArray=Arguments.createArray();

        Set<String> keys = mAdvertiserList.keySet();
        for (String key : keys) {
            BluetoothLeAdvertiser tempAdvertiser = mAdvertiserList.remove(key);
            AdvertiseCallback tempCallback = mAdvertiserCallbackList.remove(key);
            if (tempAdvertiser != null) {
                tempAdvertiser.stopAdvertising(tempCallback);
                promiseArray.pushString(key);
            }
        }

        promise.resolve(promiseArray);
    }

    @ReactMethod
	public void scanByService(String uid, ReadableMap options, Promise promise) {
        scan(uid, null, options, promise);
    }

    @ReactMethod
    public void scan(ReadableArray manufacturerPayload, ReadableMap options, Promise promise) {
        scan(null, manufacturerPayload, options, promise);
    }

	public void scan(String uid, ReadableArray manufacturerPayload, ReadableMap options, Promise promise) {
        if (mBluetoothAdapter == null) {
            promise.reject("Device does not support Bluetooth. Adapter is Null");
            return;
        }

        if (mObservedState != null && !mObservedState) {
            Log.w("BLEAdvertiserModule", "Bluetooth disabled");
            promise.reject("Bluetooth disabled");
            return;
        }

        if (mScannerCallback == null) {
            // Cannot change. 
            mScannerCallback = new SimpleScanCallback();
        } 
        
        if (mScanner == null) {
            mScanner = mBluetoothAdapter.getBluetoothLeScanner();
        } else {
            // was running. Needs to stop first. 
            mScanner.stopScan(mScannerCallback);
        }

        if (mScanner == null) {
            Log.w("BLEAdvertiserModule", "Scanner Not Available unavailable");
            promise.reject("Scanner unavailable on this device");
            return;
        } 

        ScanSettings scanSettings = buildScanSettings(options);
    
        List<ScanFilter> filters = new ArrayList<>();
        if (manufacturerPayload == null)
            filters = null;
        if (manufacturerPayload != null)
            filters.add(new ScanFilter.Builder().setManufacturerData(companyId, toByteArray(manufacturerPayload)).build());
        if (uid != null) 
            filters.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uid)).build());
        
        mScanner.startScan(filters, scanSettings, mScannerCallback);
        promise.resolve("Scanner started");
    }

    @ReactMethod
    public void addListener(String eventName) {
        // Required for RN event emitter
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        // Required for RN event emitter
    }
    
    /**
     * Broadcast as an iBeacon
     */
    @ReactMethod
    public void broadcastAsBeacon(String uuid, ReadableMap options, Promise promise) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "Device does not support Bluetooth. Adapter is Null");
            promise.reject("Device does not support Bluetooth. Adapter is Null");
            return;
        }
        
        if (mObservedState != null && !mObservedState) {
            Log.w(TAG, "Bluetooth disabled");
            promise.reject("Bluetooth disabled");
            return;
        }
        
        BluetoothLeAdvertiser tempAdvertiser;
        AdvertiseCallback tempCallback;
        
        // Use UUID as the key for the advertiser
        if (mAdvertiserList.containsKey(uuid)) {
            tempAdvertiser = mAdvertiserList.remove(uuid);
            tempCallback = mAdvertiserCallbackList.remove(uuid);
            
            tempAdvertiser.stopAdvertising(tempCallback);
        } else {
            tempAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
            tempCallback = new SimpleAdvertiseCallback(promise);
        }
        
        if (tempAdvertiser == null) {
            Log.w(TAG, "Advertiser Not Available");
            promise.reject("Advertiser unavailable on this device");
            return;
        }
        
        try {
            // Parse UUID
            UUID parsedUuid = UUID.fromString(uuid);
            
            // Get major and minor from options
            int major = 1;
            int minor = 1;
            int measuredPower = -59; // Default measured power at 1 meter
            
            if (options != null) {
                if (options.hasKey("major")) {
                    major = options.getInt("major");
                }
                
                if (options.hasKey("minor")) {
                    minor = options.getInt("minor");
                }
                
                if (options.hasKey("measuredPower")) {
                    measuredPower = options.getInt("measuredPower");
                }
            }
            
            Log.d(TAG, "Broadcasting as iBeacon with UUID: " + uuid + ", major: " + major + ", minor: " + minor);
            
            // Build iBeacon advertisement data
            AdvertiseSettings settings = buildAdvertiseSettings(options);
            AdvertiseData data = buildIBeaconAdvertiseData(parsedUuid, major, minor, measuredPower);
            
            tempAdvertiser.startAdvertising(settings, data, tempCallback);
            
            mAdvertiserList.put(uuid, tempAdvertiser);
            mAdvertiserCallbackList.put(uuid, tempCallback);
            
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid UUID format", e);
            promise.reject("InvalidUUID", "UUID is not valid: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start advertising as iBeacon", e);
            promise.reject("StartAdvertisingFailed", e.getMessage());
        }
    }
    
    /**
     * Build iBeacon advertisement data
     */
    private AdvertiseData buildIBeaconAdvertiseData(UUID uuid, int major, int minor, int measuredPower) {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        
        // iBeacon layout:
        // 0x02 (iBeacon type) + 0x15 (length) + UUID (16 bytes) + major (2 bytes) + minor (2 bytes) + txPower (1 byte)
        byte[] manufacturerData = new byte[23];
        
        // Set iBeacon type and length
        manufacturerData[0] = IBEACON_TYPE;
        manufacturerData[1] = IBEACON_TYPE_LENGTH;
        
        // Convert UUID to bytes and copy to manufacturer data
        ByteBuffer bb = ByteBuffer.wrap(manufacturerData, 2, 16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        
        // Set major value (2 bytes, big endian)
        manufacturerData[18] = (byte) ((major >> 8) & 0xFF);
        manufacturerData[19] = (byte) (major & 0xFF);
        
        // Set minor value (2 bytes, big endian)
        manufacturerData[20] = (byte) ((minor >> 8) & 0xFF);
        manufacturerData[21] = (byte) (minor & 0xFF);
        
        // Set measured power
        manufacturerData[22] = (byte) measuredPower;
        
        // Add manufacturer specific data with Apple's company ID
        dataBuilder.addManufacturerData(APPLE_MANUFACTURER_ID, manufacturerData);
        
        return dataBuilder.build();
    }
    
    /**
     * Scan specifically for iBeacons
     */
    @ReactMethod
    public void scanForIBeacons(String uuid, ReadableMap options, Promise promise) {
        if (mBluetoothAdapter == null) {
            promise.reject("Device does not support Bluetooth. Adapter is Null");
            return;
        }
        
        if (mObservedState != null && !mObservedState) {
            Log.w(TAG, "Bluetooth disabled");
            promise.reject("Bluetooth disabled");
            return;
        }
        
        if (mScannerCallback == null) {
            mScannerCallback = new SimpleScanCallback();
        }
        
        if (mScanner == null) {
            mScanner = mBluetoothAdapter.getBluetoothLeScanner();
        } else {
            mScanner.stopScan(mScannerCallback);
        }
        
        if (mScanner == null) {
            Log.w(TAG, "Scanner Not Available");
            promise.reject("Scanner unavailable on this device");
            return;
        }
        
        ScanSettings scanSettings = buildScanSettings(options);
        
        // For iBeacon scanning, we'll scan for all devices and filter in the callback
        // This is because iBeacons use manufacturer data which can't be filtered directly
        mScanner.startScan(null, scanSettings, mScannerCallback);
        promise.resolve("Scanning for iBeacons");
    }
    
    @ReactMethod
	public void stopScan(Promise promise) {
        if (mBluetoothAdapter == null) {
            promise.reject("Device does not support Bluetooth. Adapter is Null");
            return;
        } 

        if (mObservedState != null && !mObservedState) {
            Log.w("BLEAdvertiserModule", "Bluetooth disabled");
            promise.reject("Bluetooth disabled");
            return;
        }

        if (mScanner != null) {
            mScanner.stopScan(mScannerCallback);
            mScanner = null;
            promise.resolve("Scanner stopped");
        } else {
            promise.resolve("Scanner not started");
        }
    }

    private ScanSettings buildScanSettings(ReadableMap options) {
        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();

        if (options != null && options.hasKey("scanMode")) {
            scanSettingsBuilder.setScanMode(options.getInt("scanMode"));
        } 

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (options != null && options.hasKey("numberOfMatches")) {
                scanSettingsBuilder.setNumOfMatches(options.getInt("numberOfMatches"));
            }
            if (options != null && options.hasKey("matchMode")) {
                scanSettingsBuilder.setMatchMode(options.getInt("matchMode"));
            }
        }

        if (options != null && options.hasKey("reportDelay")) {
            scanSettingsBuilder.setReportDelay(options.getInt("reportDelay"));
        }

        return scanSettingsBuilder.build();
    }

    private class SimpleScanCallback extends ScanCallback {
  @Override
  public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "Scanned: " + result.toString());

            WritableMap params = Arguments.createMap();
            WritableArray paramsUUID = Arguments.createArray();

            if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
                for (ParcelUuid uuid : result.getScanRecord().getServiceUuids()) {
                    paramsUUID.pushString(uuid.toString());
                }
            }

            params.putArray("serviceUuids", paramsUUID);
            params.putInt("rssi", result.getRssi());
            
            if (result.getScanRecord() != null) {
                params.putInt("txPower", result.getScanRecord().getTxPowerLevel());
                params.putString("deviceName", result.getScanRecord().getDeviceName() != null ? result.getScanRecord().getDeviceName() : "");
                params.putInt("advFlags", result.getScanRecord().getAdvertiseFlags());
                
                // Check for manufacturer data from our company ID
                if (result.getScanRecord().getManufacturerSpecificData(companyId) != null) {
                    params.putInt("companyId", companyId);
                    params.putArray("manufData", toByteArray(result.getScanRecord().getManufacturerSpecificData(companyId)));
                }
                
                // Check for iBeacon data (Apple's company ID)
                byte[] appleData = result.getScanRecord().getManufacturerSpecificData(APPLE_MANUFACTURER_ID);
                if (appleData != null && appleData.length >= 23) {
                    // Check for iBeacon type and length
                    if (appleData[0] == IBEACON_TYPE && appleData[1] == IBEACON_TYPE_LENGTH) {
                        // Extract UUID (16 bytes)
                        ByteBuffer bb = ByteBuffer.wrap(appleData, 2, 16);
                        long high = bb.getLong();
                        long low = bb.getLong();
                        UUID proximityUuid = new UUID(high, low);
                        
                        // Extract major (2 bytes)
                        int major = ((appleData[18] & 0xFF) << 8) | (appleData[19] & 0xFF);
                        
                        // Extract minor (2 bytes)
                        int minor = ((appleData[20] & 0xFF) << 8) | (appleData[21] & 0xFF);
                        
                        // Extract measured power (1 byte)
                        int measuredPower = (int) appleData[22];
                        
                        // Add beacon data to params
                        WritableMap beaconData = Arguments.createMap();
                        beaconData.putString("uuid", proximityUuid.toString());
                        beaconData.putInt("major", major);
                        beaconData.putInt("minor", minor);
                        beaconData.putInt("measuredPower", measuredPower);
                        beaconData.putBoolean("isBeacon", true);
                        
                        params.putMap("beaconData", beaconData);
                        
                        // Calculate approximate distance based on RSSI and measured power
                        double rssi = result.getRssi();
                        double ratio = rssi / measuredPower;
                        double distance;
                        
                        if (ratio < 1.0) {
                            distance = Math.pow(ratio, 10);
                        } else {
                            distance = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
                        }
                        
                        params.putDouble("distance", distance);
                        
                        // Process beacon for monitoring and ranging
                        processBeacon(proximityUuid, major, minor, result.getRssi(), measuredPower, distance);
                    }
                }
            }
            
            if (result.getDevice() != null) {
                params.putString("deviceAddress", result.getDevice().getAddress());
            }

            sendEvent("onDeviceFound", params);
  }

  @Override
  public void onBatchScanResults(final List<ScanResult> results) {
            for (ScanResult result : results) {
                onScanResult(0, result);
            }
  }

  @Override
  public void onScanFailed(final int errorCode) {
            Log.e(TAG, "Scan failed with error code: " + errorCode);
  }
 };
 
 /**
     * Process a detected beacon for monitoring and ranging
     */
    private void processBeacon(UUID uuid, int major, int minor, int rssi, int measuredPower, double distance) {
        // Check if this beacon matches any monitored regions
        for (BeaconRegion region : monitoredRegions.values()) {
            if (matchesRegion(region, uuid, major, minor)) {
                // This beacon is in a monitored region
                // In a real implementation, we would track enter/exit events
                // For simplicity, we'll just emit the region event
                
                WritableMap params = Arguments.createMap();
                params.putString("identifier", region.identifier);
                params.putString("uuid", uuid.toString());
                params.putInt("major", major);
                params.putInt("minor", minor);
                
                sendEvent("onRegionEnter", params);
            }
        }
        
        // Check if this beacon matches any ranged regions
        for (BeaconRegion region : rangedRegions.values()) {
            if (matchesRegion(region, uuid, major, minor)) {
                // This beacon is in a ranged region
                // Collect beacons for each region and emit events
                
                WritableMap beaconInfo = Arguments.createMap();
                beaconInfo.putString("uuid", uuid.toString());
                beaconInfo.putInt("major", major);
                beaconInfo.putInt("minor", minor);
                beaconInfo.putInt("rssi", rssi);
                beaconInfo.putDouble("accuracy", distance);
                
                // Determine proximity string based on distance
                String proximityString;
                if (distance < 0.5) {
                    proximityString = "immediate";
                } else if (distance < 3.0) {
                    proximityString = "near";
                } else {
                    proximityString = "far";
                }
                beaconInfo.putString("proximity", proximityString);
                
                WritableArray beacons = Arguments.createArray();
                beacons.pushMap(beaconInfo);
                
                WritableMap params = Arguments.createMap();
                params.putString("identifier", region.identifier);
                params.putArray("beacons", beacons);
                
                sendEvent("onBeaconDiscovered", params);
            }
        }
    }
    
    /**
     * Check if a beacon matches a region
     */
    private boolean matchesRegion(BeaconRegion region, UUID uuid, int major, int minor) {
        // Check UUID
        if (!region.uuid.equals(uuid)) {
            return false;
        }
        
        // Check major if specified
        if (region.major != null && region.major != major) {
            return false;
        }
        
        // Check minor if specified
        if (region.minor != null && region.minor != minor) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Start monitoring for an iBeacon region
     */
    @ReactMethod
    public void startMonitoringForRegion(String uuid, ReadableMap options, Promise promise) {
        try {
            // Parse UUID
            UUID parsedUuid = UUID.fromString(uuid);
            
            // Get identifier, major, and minor from options
            String identifier = uuid;
            Integer major = null;
            Integer minor = null;
            
            if (options != null) {
                if (options.hasKey("identifier")) {
                    identifier = options.getString("identifier");
                }
                
                if (options.hasKey("major")) {
                    major = options.getInt("major");
                }
                
                if (options.hasKey("minor")) {
                    minor = options.getInt("minor");
                }
            }
            
            // Create beacon region
            BeaconRegion region = new BeaconRegion(identifier, parsedUuid, major, minor);
            
            // Store the region
            monitoredRegions.put(identifier, region);
            
            Log.d(TAG, "Started monitoring region: " + region.toString());
            
            // On Android, we'll detect region enter/exit events during scanning
            // by filtering scan results in the callback
            
            WritableMap response = Arguments.createMap();
            response.putString("message", "Started monitoring region");
            response.putString("identifier", identifier);
            
            promise.resolve(response);
            
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid UUID format", e);
            promise.reject("InvalidUUID", "UUID is not valid: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start monitoring region", e);
            promise.reject("StartMonitoringFailed", e.getMessage());
        }
    }
    
    /**
     * Stop monitoring for an iBeacon region
     */
    @ReactMethod
    public void stopMonitoringForRegion(String identifier, Promise promise) {
        BeaconRegion region = monitoredRegions.remove(identifier);
        
        if (region != null) {
            Log.d(TAG, "Stopped monitoring region: " + region.toString());
            
            WritableMap response = Arguments.createMap();
            response.putString("message", "Stopped monitoring region");
            response.putString("identifier", identifier);
            
            promise.resolve(response);
        } else {
            promise.reject("RegionNotFound", "No monitored region with that identifier");
        }
    }
    
    /**
     * Get all monitored regions
     */
    @ReactMethod
    public void getMonitoredRegions(Promise promise) {
        WritableArray regions = Arguments.createArray();
        
        for (Map.Entry<String, BeaconRegion> entry : monitoredRegions.entrySet()) {
            BeaconRegion region = entry.getValue();
            
            WritableMap regionMap = Arguments.createMap();
            regionMap.putString("identifier", region.identifier);
            regionMap.putString("uuid", region.uuid.toString());
            
            if (region.major != null) {
                regionMap.putInt("major", region.major);
            }
            
            if (region.minor != null) {
                regionMap.putInt("minor", region.minor);
            }
            
            regions.pushMap(regionMap);
        }
        
        promise.resolve(regions);
    }
    
    /**
     * Start ranging beacons in a region
     */
    @ReactMethod
    public void startRangingBeaconsInRegion(String uuid, ReadableMap options, Promise promise) {
        try {
            // Parse UUID
            UUID parsedUuid = UUID.fromString(uuid);
            
            // Get identifier, major, and minor from options
            String identifier = uuid;
            Integer major = null;
            Integer minor = null;
            
            if (options != null) {
                if (options.hasKey("identifier")) {
                    identifier = options.getString("identifier");
                }
                
                if (options.hasKey("major")) {
                    major = options.getInt("major");
                }
                
                if (options.hasKey("minor")) {
                    minor = options.getInt("minor");
                }
            }
            
            // Create beacon region
            BeaconRegion region = new BeaconRegion(identifier, parsedUuid, major, minor);
            
            // Store the region
            rangedRegions.put(identifier, region);
            
            Log.d(TAG, "Started ranging beacons in region: " + region.toString());
            
            // On Android, we'll detect beacons during scanning
            // by filtering scan results in the callback
            
            WritableMap response = Arguments.createMap();
            response.putString("message", "Started ranging beacons");
            response.putString("identifier", identifier);
            
            promise.resolve(response);
            
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid UUID format", e);
            promise.reject("InvalidUUID", "UUID is not valid: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ranging beacons", e);
            promise.reject("StartRangingFailed", e.getMessage());
        }
    }
    
    /**
     * Stop ranging beacons in a region
     */
    @ReactMethod
    public void stopRangingBeaconsInRegion(String identifier, Promise promise) {
        BeaconRegion region = rangedRegions.remove(identifier);
        
        if (region != null) {
            Log.d(TAG, "Stopped ranging beacons in region: " + region.toString());
            
            WritableMap response = Arguments.createMap();
            response.putString("message", "Stopped ranging beacons");
            response.putString("identifier", identifier);
            
            promise.resolve(response);
        } else {
            promise.reject("RegionNotFound", "No ranged region with that identifier");
        }
    }
    
    /**
     * Get all ranged regions
     */
    @ReactMethod
    public void getRangedRegions(Promise promise) {
        WritableArray regions = Arguments.createArray();
        
        for (Map.Entry<String, BeaconRegion> entry : rangedRegions.entrySet()) {
            BeaconRegion region = entry.getValue();
            
            WritableMap regionMap = Arguments.createMap();
            regionMap.putString("identifier", region.identifier);
            regionMap.putString("uuid", region.uuid.toString());
            
            if (region.major != null) {
                regionMap.putInt("major", region.major);
            }
            
            if (region.minor != null) {
                regionMap.putInt("minor", region.minor);
            }
            
            regions.pushMap(regionMap);
        }
        
        promise.resolve(regions);
    }

    @ReactMethod
    public void enableAdapter() {
        if (mBluetoothAdapter == null) {
            return;
        }

        if (mBluetoothAdapter.getState() != BluetoothAdapter.STATE_ON && mBluetoothAdapter.getState() != BluetoothAdapter.STATE_TURNING_ON) {
            mBluetoothAdapter.enable();
        }
    }

    @ReactMethod
    public void disableAdapter() {
        if (mBluetoothAdapter == null) {
            return;
        }

        if (mBluetoothAdapter.getState() != BluetoothAdapter.STATE_OFF && mBluetoothAdapter.getState() != BluetoothAdapter.STATE_TURNING_OFF) {
            mBluetoothAdapter.disable();
        }
    }

    @ReactMethod
    public void getAdapterState(Promise promise) {
        if (mBluetoothAdapter == null) {
            Log.w("BLEAdvertiserModule", "Device does not support Bluetooth. Adapter is Null");
            promise.reject("Device does not support Bluetooth. Adapter is Null");
            return;
        }

        Log.d(TAG, "GetAdapter State" + String.valueOf(mBluetoothAdapter.getState()));

        switch (mBluetoothAdapter.getState()) {
            case BluetoothAdapter.STATE_OFF:
                promise.resolve("STATE_OFF"); break;
            case BluetoothAdapter.STATE_TURNING_ON:
                promise.resolve("STATE_TURNING_ON"); break;
            case BluetoothAdapter.STATE_ON:
                promise.resolve("STATE_ON"); break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                promise.resolve("STATE_TURNING_OFF"); break;
        }

        promise.resolve(String.valueOf(mBluetoothAdapter.getState()));
    }

    @ReactMethod
    public void isActive(Promise promise) {
        if (mBluetoothAdapter == null) {
            Log.w("BLEAdvertiserModule", "Device does not support Bluetooth. Adapter is Null");
            promise.resolve(false);
            return;
        }

        Log.d(TAG, "GetAdapter State" + String.valueOf(mBluetoothAdapter.getState()));
        promise.resolve(mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON); 
    }

    private AdvertiseSettings buildAdvertiseSettings(ReadableMap options) {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

        if (options != null && options.hasKey("advertiseMode")) {
            settingsBuilder.setAdvertiseMode(options.getInt("advertiseMode"));
        }

        if (options != null && options.hasKey("txPowerLevel")) {
            settingsBuilder.setTxPowerLevel(options.getInt("txPowerLevel"));
        }

        if (options != null && options.hasKey("connectable")) {
            settingsBuilder.setConnectable(options.getBoolean("connectable"));
        }

        return settingsBuilder.build();
    }

    private AdvertiseData buildAdvertiseData(ParcelUuid uuid, String serviceData, ReadableMap options) {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();

        if (options != null && options.hasKey("includeDeviceName"))
            dataBuilder.setIncludeDeviceName(options.getBoolean("includeDeviceName"));
        
        if (options != null && options.hasKey("includeTxPowerLevel"))
            dataBuilder.setIncludeTxPowerLevel(options.getBoolean("includeTxPowerLevel"));
        
        // Add service UUID
        dataBuilder.addServiceUuid(uuid);
        
        // Add service data
        if (serviceData != null && !serviceData.isEmpty()) {
            byte[] serviceDataBytes = serviceData.getBytes();
            dataBuilder.addServiceData(uuid, serviceDataBytes);
            Log.d(TAG, "Added service data: " + serviceData);
        }
        
        return dataBuilder.build();
    }

    private class SimpleAdvertiseCallback extends AdvertiseCallback {
        Promise promise;

        public SimpleAdvertiseCallback () {
        }

        public SimpleAdvertiseCallback (Promise promise) {
            this.promise = promise;
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.i(TAG, "Advertising failed with code "+ errorCode);

            if (promise == null) return;

            switch (errorCode) {
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    promise.reject("This feature is not supported on this platform."); break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    promise.reject("Failed to start advertising because no advertising instance is available."); break;
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    promise.reject("Failed to start advertising as the advertising is already started."); break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    promise.reject("Failed to start advertising as the advertise data to be broadcasted is larger than 31 bytes."); break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    promise.reject("Operation failed due to an internal error."); break;
            }
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.i(TAG, "Advertising successful");

            if (promise == null) return;
            promise.resolve(settingsInEffect.toString());
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                final int prevState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR);
                
                Log.d(TAG, String.valueOf(state));
                switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    mObservedState = false;
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    mObservedState = false;
                    break;
                case BluetoothAdapter.STATE_ON:
                    mObservedState = true;
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    mObservedState = true;
                    break;
                }

                // Only send enabled when fully ready. Turning on and Turning OFF are seen as disabled. 
                if (state == BluetoothAdapter.STATE_ON && prevState != BluetoothAdapter.STATE_ON) {
                    WritableMap params = Arguments.createMap();
                    params.putBoolean("enabled", true);
                    sendEvent("onBTStatusChange", params);
                } else if (state != BluetoothAdapter.STATE_ON && prevState == BluetoothAdapter.STATE_ON ) {
                    WritableMap params = Arguments.createMap();
                    params.putBoolean("enabled", false);
                    sendEvent("onBTStatusChange", params);
                }
            }
        }
    };

    private void sendEvent(String eventName, WritableMap params) {
        getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    // @Override
    // public void onCreate() {
    //     super.onCreate();
    //     // Register for broadcasts on BluetoothAdapter state change
    //     IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    //     registerReceiver(mReceiver, filter);
    // }

    // @Override
    // public void onDestroy() {
    //     super.onDestroy();
    //     unregisterReceiver(mReceiver);
    // }
}
