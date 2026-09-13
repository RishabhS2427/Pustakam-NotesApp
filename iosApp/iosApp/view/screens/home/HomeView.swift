import SwiftUI

struct HomeView : View {
    @State var selectedTab = 0
    // 💬 31-Aug-2026: the tab badge. Chat runs for the whole signed-in app, not only while its
    //   tab is on screen, so a message arriving while you write a note still lights this up.
    @StateObject private var chatSession = ChatSessionModel()
    @Environment(\.scenePhase) private var scenePhase
    private var unreadChats: Int { chatSession.totalUnread }
    // 🎨 22-Jul-2026 — palette drives the tab bar tint so the accent follows the chosen theme
    @Environment(\.palette) private var palette
    var title : String {
        switch(selectedTab) {
            case 0 :  return "Notes"
            case 1 :  return "Search"
            case 2 :  return "Chat"
            case 3 :  return "Notification"
            case 4 :  return "Settings"
            default : return "Notes"
        }
    }
    var body: some View {
        TabView(selection: $selectedTab){
            NotesView()
                .background(Theme.Colors.background)
                .tabItem {
                Image("book_icon")
                Text("Notes")
            }
            .tag(0)
            SearchView()
                .background(Theme.Colors.background)
                .tabItem {
                Image(systemName: "magnifyingglass")
                Text("Search")
            }
            .tag(1)
            
            // 💬 31-Aug-2026 chat: sits between search and notifications, matching Android
            ChatListView()
                .background(Theme.Colors.background)
                .tabItem {
                Image(systemName: "bubble.left.and.bubble.right.fill")
                Text("Chat")
            }
            .badge(unreadChats)
            .tag(2)

            NotificationView()
                .background(Theme.Colors.background)
                .tabItem {
                Image(systemName: "bell.fill")
                Text("Notification")
            }
            .tag(3)
            
            SettingsView()
                .background(Theme.Colors.background)
                .tabItem {
                Image(systemName: "gearshape.fill")
                Text("Settings")
            }
            .tag(4)
        }
        .navigationBarBackButtonHidden(true)
        .navigationTitle(title.capitalized)
        .onAppear { chatSession.start() }
        .onChange(of: scenePhase) { _, phase in chatSession.setForeground(phase == .active) }
        // 🎨 22-Jul-2026 — saffron/copper accent on the selected tab instead of the system blue
        .tint(palette.accent)

    }
}

// 🎨 22-Jul-2026 — previews per theme so the tab bar tint can be checked without running the app
#Preview("Home — Light") {
    HomeView().environment(\.palette, ThemePalette(mode: .light, scheme: .light))
        .preferredColorScheme(.light)
}

#Preview("Home — Dark") {
    HomeView().environment(\.palette, ThemePalette(mode: .dark, scheme: .dark))
        .preferredColorScheme(.dark)
}

#Preview("Home — AMOLED") {
    HomeView().environment(\.palette, ThemePalette(mode: .amoled, scheme: .dark))
        .preferredColorScheme(.dark)
}


