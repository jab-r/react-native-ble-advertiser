module.exports = {
  dependencies: {
    'react-native-ble-advertiser': {
        platforms: {
            android: {
                "packageImportPath": "import com.jabresearch.bleadvertiser;",
                "packageInstance": "new BLEAdvertiserPackage()"
            },
            ios: {
                "podspecPath": "ios/react-native-ble-advertiser.podspec"
            }
        }
    }
  }
};