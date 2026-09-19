import Foundation

enum Gateway {
    static let base = URL(string: "https://api.miaolibrary.in")!
    static func login(username: String, password: String) async throws -> String {
        let json = try await post("/login", body: ["username": username, "password": password])
        if let token = json["access_token"] as? String, !token.isEmpty { return token }
        if let token = json["token"] as? String, !token.isEmpty { return token }
        throw URLError(.cannotParseResponse)
    }
    static func account(token: String) async throws -> Member {
        let json = try await get("/account", token: token)
        return Member(name: value(json, "name"), cardNumber: value(json, "card_number"), email: value(json, "email"), expiry: value(json, "membership_expiry_date"), status: value(json, "membership_status"))
    }
    static func books(token: String) async throws -> [Book] { array(try await get("/my-books", token: token)).map(Book.init) }
    static func history(token: String) async throws -> [[String: Any]] { array(try await get("/issue-history", token: token)) }
    private static func get(_ path: String, token: String) async throws -> [String: Any] {
        var request = URLRequest(url: base.appendingPathComponent(String(path.dropFirst())))
        request.httpMethod = "GET"; request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization"); request.setValue("application/json", forHTTPHeaderField: "Accept"); request.setValue("no-cache", forHTTPHeaderField: "Cache-Control"); request.setValue("no-cache", forHTTPHeaderField: "Pragma")
        let (data, response) = try await URLSession.shared.data(for: request); try validate(response)
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
    private static func post(_ path: String, body: [String: Any]) async throws -> [String: Any] {
        var request = URLRequest(url: base.appendingPathComponent(String(path.dropFirst())))
        request.httpMethod = "POST"; request.setValue("application/json", forHTTPHeaderField: "Content-Type"); request.setValue("application/json", forHTTPHeaderField: "Accept"); request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request); try validate(response)
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
    private static func validate(_ response: URLResponse) throws { guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { throw URLError(.badServerResponse) } }
    private static func value(_ json: [String: Any], _ key: String) -> String { if let v = json[key] as? String { return v }; if let d = json["data"] as? [String: Any], let v = d[key] as? String { return v }; return "" }
    private static func array(_ json: [String: Any]) -> [[String: Any]] { for key in ["books","items","issues","history","records","data"] { if let a = json[key] as? [[String: Any]] { return a } }; return [] }
}
