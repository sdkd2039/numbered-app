import SwiftUI

@main
struct NumberedApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView().environment(\.layoutDirection, .rightToLeft)
        }
    }
}
