import Foundation
import UserNotifications

enum DueDateNotifications {
    static func sync(books: [Book]) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: books.map { "due-\($0.id)" })
        for book in books {
            guard let due = parse(book.dueDate) else { continue }
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = TimeZone(identifier: "Asia/Kolkata")!
            var components = calendar.dateComponents([.year,.month,.day], from: due)
            components.hour = 9; components.minute = 0; components.second = 0
            let content = UNMutableNotificationContent()
            content.title = "Book due today"; content.body = book.title; content.sound = .default
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            center.add(UNNotificationRequest(identifier: "due-\(book.id)", content: content, trigger: trigger))
        }
    }
    private static func parse(_ value: String) -> Date? {
        for format in ["yyyy-MM-dd","dd/MM/yyyy","MM/dd/yyyy","yyyy-MM-dd HH:mm:ss"] {
            let f = DateFormatter(); f.locale = Locale(identifier:"en_US_POSIX"); f.timeZone = TimeZone(identifier:"Asia/Kolkata"); f.dateFormat = format
            if let d = f.date(from:value) { return d }
        }
        return nil
    }
}
