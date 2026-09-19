import SwiftUI
import UserNotifications

@main
struct MiaoLibraryApp: App {
    @StateObject private var session = SessionStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .task {
                    await session.restore()
                    try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
                }
        }
    }
}
