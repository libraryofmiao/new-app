import Foundation
import Combine

struct Book: Identifiable, Codable, Hashable {
    let id: String
    let title: String
    let author: String
    let dueDate: String
    let checkoutDate: String
    let biblionumber: String?
    let library: String?
    let availability: String?
    init(json: [String: Any]) {
        id = String(json["issue_id"] ?? json["itemnumber"] ?? json["barcode"] ?? UUID().uuidString)
        title = String(json["title"] ?? json["name"] ?? "Untitled")
        author = String(json["author"] ?? "")
        dueDate = String(json["date_due"] ?? json["due_date"] ?? json["due"] ?? "")
        checkoutDate = String(json["issuedate"] ?? json["checkout_date"] ?? json["date_issued"] ?? "")
        biblionumber = (json["biblionumber"] as? CustomStringConvertible)?.description
        library = (json["library"] as? CustomStringConvertible)?.description
        availability = (json["availability"] as? CustomStringConvertible)?.description
    }
}
struct Member: Codable { var name: String; var cardNumber: String; var email: String; var expiry: String; var status: String }

final class SessionStore: ObservableObject {
    @Published var token: String?
    @Published var username = ""
    @Published var member: Member?
    @Published var books: [Book] = []
    @Published var loggedIn = false
    private let key = "miao_access_token", userKey = "miao_username", cachePrefix = "miao_issued_books_", dailyPrefix = "miao_daily_fetch_"

    func restore() async {
        token = Keychain.get(key); username = UserDefaults.standard.string(forKey: userKey) ?? ""; loggedIn = token != nil
        if loggedIn { member = loadMember(); books = loadCachedBooks(); await refreshProfileIfNeeded(); await syncIssuedBooks(); DueDateNotifications.sync(books: books) }
    }
    func login(username: String, password: String) async throws {
        let result = try await Gateway.login(username: username, password: password)
        token = result; self.username = username; Keychain.set(result, key: key); UserDefaults.standard.set(username, forKey: userKey); loggedIn = true
        member = try await Gateway.account(token: result); saveMember(); await syncIssuedBooks(force: true); DueDateNotifications.sync(books: books)
    }
    func logout() { token = nil; loggedIn = false; member = nil; books = []; Keychain.delete(key); UserDefaults.standard.removeObject(forKey: userKey) }
    func syncIssuedBooks(force: Bool = false) async {
        guard let token else { return }
        let cache = loadCachedBooks()
        if !cache.isEmpty && !force && !dailyFetchIsDue() { books = cache; return }
        do {
            let fresh = try await Gateway.books(token: token); books = fresh; saveCachedBooks(fresh)
            UserDefaults.standard.set(ISO8601DateFormatter().string(from: Date()), forKey: dailyPrefix + patronKey)
            DueDateNotifications.sync(books: fresh)
        } catch { books = cache; DueDateNotifications.sync(books: cache) }
    }
    private var patronKey: String { (member?.cardNumber.isEmpty == false ? member!.cardNumber : username).replacingOccurrences(of: "/", with: "_") }
    private func dailyFetchIsDue() -> Bool {
        guard let raw = UserDefaults.standard.string(forKey: dailyPrefix + patronKey), let date = ISO8601DateFormatter().date(from: raw) else { return true }
        var india = Calendar(identifier: .gregorian); india.timeZone = TimeZone(identifier: "Asia/Kolkata")!
        let today = india.dateComponents([.year,.month,.day], from: Date())
        let fetchedDay = india.dateComponents([.year,.month,.day], from: date)
        return today != fetchedDay && india.component(.hour, from: Date()) >= 17
    }
    private func saveCachedBooks(_ value: [Book]) { if let data = try? JSONEncoder().encode(value) { UserDefaults.standard.set(data, forKey: cachePrefix + patronKey) } }
    private func loadCachedBooks() -> [Book] { guard let data = UserDefaults.standard.data(forKey: cachePrefix + patronKey) else { return [] }; return (try? JSONDecoder().decode([Book].self, from: data)) ?? [] }
    private func saveMember() { guard let member, let data = try? JSONEncoder().encode(member) else { return }; UserDefaults.standard.set(data, forKey: "miao_member") }
    private func loadMember() -> Member? { guard let data = UserDefaults.standard.data(forKey: "miao_member") else { return nil }; return try? JSONDecoder().decode(Member.self, from: data) }
    private func refreshProfileIfNeeded() async { guard let token, member == nil, let m = try? await Gateway.account(token: token) else { return }; member = m; saveMember() }
}
