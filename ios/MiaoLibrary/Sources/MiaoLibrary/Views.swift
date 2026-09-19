import SwiftUI

struct RootView: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        Group {
            if session.loggedIn { MainTabView() } else { LoginView() }
        }
        .tint(Color(red: 27/255, green: 43/255, blue: 58/255))
    }
}

struct LoginView: View {
    @EnvironmentObject var session: SessionStore
    @State private var username = ""
    @State private var password = ""
    @State private var error = ""
    var body: some View {
        ZStack {
            Color(red: 248/255, green: 245/255, blue: 239/255).ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "books.vertical.fill").font(.system(size: 64)).padding(.bottom, 12)
                Text("Miao Library").font(.largeTitle.bold())
                Text("Your library, wherever you are").foregroundStyle(.secondary)
                TextField("Library username", text: $username).textInputAutocapitalization(.never).autocorrectionDisabled().textFieldStyle(.roundedBorder)
                SecureField("Password", text: $password).textFieldStyle(.roundedBorder)
                if !error.isEmpty { Text(error).foregroundStyle(.red).font(.footnote) }
                Button("Sign in") {
                    Task {
                        do { try await session.login(username: username.trimmingCharacters(in: .whitespacesAndNewlines), password: password) }
                        catch { error = "Login failed. Please check your credentials." }
                    }
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
            }.padding(28)
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            HomeView().tabItem { Label("Home", systemImage: "house") }
            CatalogueView().tabItem { Label("Catalogue", systemImage: "magnifyingglass") }
            BooksView().tabItem { Label("My Books", systemImage: "book.closed") }
            AccountView().tabItem { Label("Account", systemImage: "person.crop.circle") }
        }
    }
}

struct HomeView: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Welcome back, \(firstName)").font(.title2.bold())
                    MemberCard()
                    Text("Library announcements").font(.headline)
                    Text("Announcements and events published by the library will appear here.")
                        .foregroundStyle(.secondary)
                }.padding()
            }.navigationTitle("Miao Library")
        }
    }
    private var firstName: String {
        session.member?.name.split(separator: " ").first.map(String.init) ?? session.username
    }
}

struct MemberCard: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(session.member?.name ?? "").font(.headline)
            Text("Card No.: \(session.member?.cardNumber ?? "")")
            Text("Email: \(session.member?.email ?? "")")
            Text("Valid Upto: \(session.member?.expiry ?? "")")
            Text("Membership Status: \(session.member?.status ?? "")")
        }.frame(maxWidth: .infinity, alignment: .leading)
         .padding()
         .background(.background, in: RoundedRectangle(cornerRadius: 20))
         .shadow(radius: 3, y: 2)
    }
}

struct BooksView: View {
    @EnvironmentObject var session: SessionStore
    @State private var history = ""
    var body: some View {
        NavigationStack {
            List {
                Section("Currently issued") {
                    if session.books.isEmpty { Text("No books currently issued.").foregroundStyle(.secondary) }
                    ForEach(session.books) { book in
                        VStack(alignment: .leading, spacing: 5) {
                            Text(book.title).font(.headline)
                            if !book.author.isEmpty { Text(book.author).foregroundStyle(.secondary) }
                            if !book.dueDate.isEmpty { Text("Due: \(book.dueDate)").font(.subheadline) }
                        }
                    }
                    Button("Refresh issued books") { Task { await session.syncIssuedBooks(force: true) } }
                }
                Section("Previous Issues") {
                    Button("Load Issue History / Previous Issues") {
                        Task {
                            guard let token = session.token else { return }
                            if let records = try? await Gateway.history(token: token) {
                                history = records.map { String($0["title"] ?? "Untitled") }.joined(separator: "\n")
                            }
                        }
                    }
                    if !history.isEmpty { Text(history) }
                }
            }.navigationTitle("My Books")
        }
    }
}

struct CatalogueView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView("Catalogue", systemImage: "magnifyingglass", description: Text("OPAC catalogue search will use the read-only gateway endpoint."))
                .navigationTitle("Catalogue")
        }
    }
}

struct AccountView: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        NavigationStack {
            List {
                Section("Library account") {
                    Text(session.member?.name ?? session.username)
                    Text("Card No.: \(session.member?.cardNumber ?? "")")
                    Text("Membership: \(session.member?.status ?? "")")
                }
                Section {
                    Button("Log out", role: .destructive) { session.logout() }
                }
                Section {
                    Text("Our Official Website : miaolibrary.in")
                    Text("Developed By : A. M. Tripathi")
                    Text("© Sub Divisional Library Miao. All Rights Reserved.")
                        .foregroundStyle(.secondary)
                }
            }.navigationTitle("Account")
        }
    }
}
