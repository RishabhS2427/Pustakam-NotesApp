import SwiftUI
import shared

struct ShareToChatSheet: View {

    let conversationId: String

    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = ChatShareViewModel()

    var body: some View {
        let state = viewModel.state
        NavigationStack {
            List {
                Section {
                    Text("Everyone in this chat gets their own shared version of the note.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                    Picker("Access", selection: Binding(get: { state.role }, set: { viewModel.choose($0) })) {
                        ForEach(state.roles, id: \.self) { role in
                            Text(ShareSheet.shared.label(role: role)).tag(role)
                        }
                    }
                    .pickerStyle(.segmented)
                    TextField("Search your notes", text: Binding(get: { state.query }, set: { viewModel.onQueryChange($0) }))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Section {
                    ForEach(state.notes, id: \.id) { note in
                        Button {
                            viewModel.pick(note.id)
                        } label: {
                            HStack {
                                Text(ChatShareSheet.shared.title(note: note))
                                    .lineLimit(1)
                                    .foregroundColor(.primary)
                                Spacer()
                                if state.pickedId == note.id {
                                    Image(systemName: "checkmark.circle.fill").foregroundColor(.accentColor)
                                }
                            }
                        }
                    }
                }
                Section {
                    Button(state.busy ? "Sharing…" : "Share in chat") { viewModel.send() }
                        .disabled(!state.canSend)
                    if let error = state.error {
                        Text(error)
                            .font(.footnote)
                            .foregroundColor(.red)
                            .onTapGesture { viewModel.clearError() }
                    }
                }
            }
            .navigationTitle("Share a note")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .onAppear { viewModel.open(conversationId: conversationId) }
        .onChange(of: state.sent) { _, sent in if sent { dismiss() } }
    }
}
