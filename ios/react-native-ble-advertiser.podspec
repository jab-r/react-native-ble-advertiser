require 'json'

package = JSON.parse(File.read(File.join(__dir__, '../package.json')))

Pod::Spec.new do |s|
  s.name         = package['name']
  s.version      = package['version']
  s.summary      = package['description']
  s.authors      = package['author']
  s.homepage     = package['homepage']
  s.license      = package['license']

  s.platform     = :ios, '15.0'
  s.source       = { :git => "https://github.com/jab-r/react-native-ble-advertiser.git", :tag => "#{s.version}" }
  s.source_files = '*.{h,m}'
  s.requires_arc = true

  s.dependency 'React-Core'
  
  # Ensure the project can find the React Native headers
  s.pod_target_xcconfig = {
    'HEADER_SEARCH_PATHS' => '$(PODS_ROOT)/Headers/Public/React-Core'
  }
end
