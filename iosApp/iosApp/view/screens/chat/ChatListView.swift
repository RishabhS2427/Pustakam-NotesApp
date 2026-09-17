import SwiftUI
import shared

/// 💬 The inbox — a tab in HomeView. Tapping a row opens the full-screen thread; the button in the
/// toolbar opens the people picker, which also offers a fresh conversation with the assistant.
struct ChatListView: View {

    @StateObject private var viewModel = ChatListViewModel()
    @Environment(Router.self) private var router: Router
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.palette) private var palette

    private var state: ChatListState { viewModel.state }

    var body: some View {
        // 🔧 17-Sep-2026 — the "new chat" control is a button floating over the list, not a
        //   ToolbarItem. This view is a TAB inside HomeView's TabView, and the navigation bar
        //   belongs to HomeView, not to the tab — so a .navigationBarTrailing item declared here
        //   never appeared and there was no way to start a conversation on iOS at all. Android
        //   solves it with a FAB over the content for the same reason; this now matches it.
        ZStack(alignment: .bottomTrailing) {
            inbox
            newChatButton
        }
        .background(Theme.Colors.background)
        .sheet(isPresented: Binding(
            get: { state.isPickingPeer },
            set: { if !$0 { viewModel.closePeerPicker() } }
        )) {
            peerPicker
        }
        .onAppear { viewModel.load() }
        .onChange(of: scenePhase) { _, phase in viewModel.onForeground(phase == .active) }
        // Opening a new conversation navigates once, then the signal is consumed so reopening
        // the inbox does not jump straight back into that thread.
        .onChange(of: viewModel.openedConversationId) { _, opened in
            guard let opened else { return }
            router.navigate(to: .ChatThread(conversationId: opened, title: "Chat"))
            viewModel.openedConversationId = nil
        }
    }

    private var newChatButton: some View {
        Button { viewModel.openPeerPicker() } label: {
            Label("New chat", systemImage: "square.and.pencil")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.Colors.ivory)
                .padding(.horizontal, 18)
                .padding(.vertical, 14)
                .background(palette.accent)
                .clipShape(Capsule())
                .shadow(color: Theme.Elevation.fab, radius: Theme.Elevation.fabRadius,
                        x: 0, y: Theme.Elevation.fabY)
        }
        .padding(16)
    }

    private var inbox: some View {
        Group {
            if state.isLoading && state.conversations.isEmpty {
                LoadingUI()
            } else if state.isEmpty {
                emptyInbox
            } else {
                List {
                    ForEach(state.visibleConversations, id: \.id) { conversation in
                        ChatConversationRowView(conversation: conversation) {
                            router.navigate(to: .ChatThread(conversationId: conversation.id, title: conversation.displayTitle()))
                        }
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Theme.Colors.background)
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) { viewModel.delete(conversationId: conversation.id) } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .refreshable { viewModel.refresh() }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // filters the conversations already on the device — separate from the people search
        .searchable(text: Binding(get: { state.inboxQuery }, set: viewModel.onInboxQueryChange),
                    prompt: "Search chats")
    }

    private var peerPicker: some View {
        NavigationStack {
            List {
                // 🤖 the assistant sits at the top of the picker, not behind a separate entry point
                Button { viewModel.startAssistantChat() } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "sparkles").foregroundColor(palette.accent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Pustakam AI").font(.subheadline.weight(.semibold)).foregroundColor(Theme.Colors.text)
                            Text("Ask the assistant anything").font(.caption).foregroundColor(Theme.Colors.text3)
                        }
                        Spacer()
                    }
                }

                Section("People") {
                    if state.isPeerSearchIdle {
                        pickerHint("Type a username to find someone")
                    } else if state.isSearchingPeers && state.visiblePeers.isEmpty {
                        pickerHint("Searching…")
                    } else if state.visiblePeers.isEmpty {
                        pickerHint("No one found with that username")
                    } else {
                        ForEach(state.visiblePeers, id: \.id) { participant in
                            ChatPeerRowView(participant: participant) {
                                viewModel.startDirectChat(participantId: participant.id)
                            }
                            .listRowInsets(EdgeInsets())
                        }
                    }
                }
            }
            // 🆔 usernames only — there is no browsable directory of everyone any more
            .searchable(text: Binding(get: { state.query }, set: viewModel.onQueryChange),
                        prompt: "Search by username")
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
            .navigationTitle("Start a conversation")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func pickerHint(_ text: String) -> some View {
        Text(text)
            .font(.caption)
            .foregroundColor(Theme.Colors.text3)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.vertical, 16)
    }

    private var emptyInbox: some View {
        VStack(spacing: 10) {
            Text("No conversations yet").font(.headline).foregroundColor(Theme.Colors.text)
            Text("Start one with someone, or ask the assistant.")
                .font(.subheadline)
                .foregroundColor(Theme.Colors.text3)
                .multilineTextAlignment(.center)
            Button { viewModel.startAssistantChat() } label: {
                Label("Chat with Pustakam AI", systemImage: "sparkles")
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Theme.Colors.gold.opacity(0.25))
                    .clipShape(Capsule())
            }
            .padding(.top, 6)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
