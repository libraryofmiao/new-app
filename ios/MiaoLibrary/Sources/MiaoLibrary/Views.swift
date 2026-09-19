import SwiftUI

private let navy = Color(red: 27/255, green: 43/255, blue: 58/255)
private let paper = Color(red: 248/255, green: 245/255, blue: 239/255)
private let card = Color.white

struct RootView: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        Group { if session.loggedIn { MainTabView() } else { LoginView() } }
            .tint(navy).preferredColorScheme(.light)
    }
}

struct LoginView: View {
    @EnvironmentObject var session: SessionStore
    @State private var username = ""; @State private var password = ""; @State private var error = ""; @State private var busy = false
    var body: some View {
        ZStack {
            paper.ignoresSafeArea()
            VStack(spacing: 18) {
                Spacer()
                Image(systemName: "books.vertical.fill").font(.system(size: 58)).foregroundStyle(navy)
                Text("Miao Library").font(.system(size: 32, weight: .bold, design: .rounded)).foregroundStyle(navy)
                Text("Your library, wherever you are").foregroundStyle(.secondary)
                VStack(spacing: 12) {
                    TextField("Library username", text: $username).textInputAutocapitalization(.never).autocorrectionDisabled().padding(14).background(card).clipShape(RoundedRectangle(cornerRadius: 14))
                    SecureField("Password", text: $password).padding(14).background(card).clipShape(RoundedRectangle(cornerRadius: 14))
                }
                if !error.isEmpty { Text(error).font(.footnote).foregroundStyle(.red) }
                Button {
                    busy = true; Task { defer { busy = false }; do { try await session.login(username: username.trimmingCharacters(in: .whitespacesAndNewlines), password: password) } catch _ { self.error = "Login failed. Please check your credentials." } }
                } label: { Text(busy ? "Signing in…" : "Sign in").frame(maxWidth: .infinity).padding(.vertical, 14) }
                .buttonStyle(.borderedProminent).controlSize(.large).disabled(busy || username.isEmpty || password.isEmpty)
                Spacer()
            }.padding(24)
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            HomeView().tabItem { Label("Home", systemImage: "house.fill") }
            CatalogueView().tabItem { Label("Catalogue", systemImage: "magnifyingglass") }
            BooksView().tabItem { Label("My Books", systemImage: "book.closed.fill") }
            AccountView().tabItem { Label("Account", systemImage: "person.crop.circle.fill") }
        }
    }
}

struct ScreenBackground<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View { ZStack { paper.ignoresSafeArea(); content } }
}

struct MemberCard: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 14) {
                Circle().fill(navy.opacity(0.12)).frame(width: 58, height: 58).overlay(Image(systemName: "person.fill").font(.title2).foregroundStyle(navy))
                VStack(alignment: .leading, spacing: 3) {
                    Text(session.member?.name ?? "").font(.headline)
                    Text("Card No.: \(session.member?.cardNumber ?? "")").font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
            }
            Divider()
            InfoRow(label: "Email", value: session.member?.email ?? "")
            InfoRow(label: "Valid Upto", value: session.member?.expiry ?? "")
            HStack { Text("Membership Status").foregroundStyle(.secondary); Spacer(); Text(session.member?.status ?? "").fontWeight(.semibold).foregroundStyle(.green) }
        }
        .padding(18).background(card).clipShape(RoundedRectangle(cornerRadius: 20)).shadow(color: .black.opacity(0.08), radius: 12, y: 5)
    }
}
struct InfoRow: View {
    let label: String; let value: String
    var body: some View { HStack { Text(label).foregroundStyle(.secondary); Spacer(); Text(value).multilineTextAlignment(.trailing) } }
}

struct HomeView: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        NavigationStack {
            ScreenBackground {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        Text(welcomeText).font(.system(size: 25, weight: .bold, design: .rounded)).foregroundStyle(navy)
                        MemberCard()
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Library Announcements").font(.headline).foregroundStyle(navy)
                            Text("Announcements, events and notices published by the library will appear here.")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }.padding(18)
                }
            }.navigationTitle("Miao Library").navigationBarTitleDisplayMode(.inline)
        }
    }
    private var welcomeText: String {
        let name = session.member?.name.split(separator: " ").first.map(String.init) ?? session.username
        return "Welcome back, " + name
    }
}

struct BooksView: View {
    @EnvironmentObject var session: SessionStore
    @State private var history: [[String: Any]] = []; @State private var loadingHistory = false
    var body: some View {
        NavigationStack {
            ScreenBackground {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        if session.books.isEmpty {
                            EmptyState(title: "No books currently issued", icon: "books.vertical")
                        } else {
                            ForEach(session.books) { book in BookRow(book: book) }
                        }
                        Button { Task { await session.syncIssuedBooks(force: true) } } label: { Label("Refresh issued books", systemImage: "arrow.clockwise") }
                            .buttonStyle(.bordered).padding(.top, 4)
                        Divider().padding(.vertical, 8)
                        HStack {
                            Text("Previous Issues").font(.headline).foregroundStyle(navy)
                            Spacer()
                            Button(loadingHistory ? "Loading…" : "Load") {
                                guard let token = session.token else { return }; loadingHistory = true
                                Task { defer { loadingHistory = false }; history = (try? await Gateway.history(token: token)) ?? [] }
                            }.font(.subheadline.weight(.semibold))
                        }
                        if history.isEmpty && !loadingHistory {
                            Text("Tap Load to view previous issues.").font(.subheadline).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
                        } else {
                            ForEach(Array(history.enumerated()), id: \.offset) { _, item in
                                Text(String(item["title"] ?? item["name"] ?? "Untitled")).frame(maxWidth: .infinity, alignment: .leading).padding(14).background(card).clipShape(RoundedRectangle(cornerRadius: 14))
                            }
                        }
                    }.padding(18)
                }
            }.navigationTitle("My Books").navigationBarTitleDisplayMode(.inline)
        }
    }
}
struct BookRow: View {
    let book: Book
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(book.title).font(.headline).foregroundStyle(navy)
            if !book.author.isEmpty { Text(book.author).font(.subheadline).foregroundStyle(.secondary) }
            if !book.checkoutDate.isEmpty { Text("Issued: \(book.checkoutDate)").font(.caption).foregroundStyle(.secondary) }
            if !book.dueDate.isEmpty { Label("Due: \(book.dueDate)", systemImage: "calendar").font(.subheadline.weight(.medium)) }
        }.frame(maxWidth: .infinity, alignment: .leading).padding(16).background(card).clipShape(RoundedRectangle(cornerRadius: 17)).shadow(color: .black.opacity(0.05), radius: 7, y: 3)
    }
}
struct EmptyState: View {
    let title: String; let icon: String
    var body: some View { VStack(spacing: 10) { Image(systemName: icon).font(.system(size: 34)); Text(title).font(.headline); Text("Your issued books will appear here.").font(.subheadline).foregroundStyle(.secondary) }.frame(maxWidth: .infinity).padding(40) }
}

struct CatalogueView: View {
    @State private var query = ""
    var body: some View {
        NavigationStack {
            ScreenBackground {
                VStack(spacing: 18) {
                    HStack { Image(systemName: "magnifyingglass").foregroundStyle(.secondary); TextField("Search catalogue", text: $query).textInputAutocapitalization(.never); if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill") } } }
                        .padding(13).background(card).clipShape(RoundedRectangle(cornerRadius: 14)).padding(.horizontal, 18)
                    EmptyState(title: "Search the library catalogue", icon: "books.vertical")
                    Spacer()
                }.padding(.top, 18)
            }.navigationTitle("Catalogue").navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct AccountView: View {
    @EnvironmentObject var session: SessionStore
    var body: some View {
        NavigationStack {
            ScreenBackground {
                ScrollView {
                    VStack(spacing: 14) {
                        MemberCard()
                        Button { session.logout() } label: { Label("Log out", systemImage: "rectangle.portrait.and.arrow.right").frame(maxWidth: .infinity) }.buttonStyle(.bordered).tint(.red)
                        VStack(spacing: 5) {
                            Text("Our Official Website : miaolibrary.in")
                            Text("Developed By : A. M. Tripathi")
                            Text("© Sub Divisional Library Miao. All Rights Reserved.").foregroundStyle(.secondary)
                        }.font(.footnote).multilineTextAlignment(.center).padding(.top, 22)
                    }.padding(18)
                }
            }.navigationTitle("Account").navigationBarTitleDisplayMode(.inline)
        }
    }
}
