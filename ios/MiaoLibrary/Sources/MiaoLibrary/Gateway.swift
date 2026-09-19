import Foundation

enum Gateway {
    static let base = URL(string: "https://api.miaolibrary.in")!
    
    static func login(username: String, password: String) async throws -> String {
        try await post("/login", body: ["username": username, "password": password])["access_token"] as? String
            ?? post("/login", body: ["username": username, "password": password]).value(for: "token")
    }
    
    static func account(token: String) async throws -> Member {
        let json = try await get("/account", token: token)
        return Member(
            name: value(json, "name"),
            cardNumber: value(json, "card_number"),
            email: value(json, "email"),
            expiry: value(json, "membership_expiry_date"),
            status: value(json, "membership_status")
        )
    }
    
    static func books(token: String) async throws -> [Book] {
        let json = try await get("/my-books", token: token)
        return array(json).map(Book.init)
    }
    
    static func history(token: String) async throws -> [[String: Any]] {
        let json = try await get("/issue-history", token: token)
        return array(json)
    }
    
    private static func get(_ path: String, token: String) async throws -> [String: Any] {
        var request = URLRequest(url: base.appendingPathComponent(path.dropFirst()))
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response)
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
    
    private static func post(_ path: String, body: [String: Any]) async throws -> [String: Any] {
        var request = URLRequest(url: base.appendingPathComponent(path.dropFirst()))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response)
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
    
    private static func validate(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }
    
    private static func value(_ json: [String: Any], _ key: String) -> String {
        if let v = json[key] as? String { return v }
        if let nested = json["data"] as? [String: Any], let v = nested[key] as? String { return v }
        return ""
    }
    
    private static func array(_ json: [String: Any]) -> [[String: Any]] {
        for key in ["books", "items", "issues", "history", "records", "data"] {
            if let a = json[key] as? [[String: Any]] { return a }
        }
        return []
    }
}

private extension Dictionary where Key == String, Value == Any {
    func value(for key: String) -> String? { self[key] as? String }
}
