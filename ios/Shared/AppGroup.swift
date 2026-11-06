import Foundation

enum NumberedShared {
    static let appGroup = "group.com.sutstudio.numbered"
    static let dhikrCSV = "معدودات - الأذكار.csv"
    static let remainingCSV = "معدودات - المتبقي.csv"
    static var defaults: UserDefaults {
        UserDefaults(suiteName: appGroup) ?? .standard
    }
}
