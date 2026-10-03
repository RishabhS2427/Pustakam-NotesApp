import SwiftUI
import shared

struct ShareNoteTarget: Identifiable {
    let id: String
}

struct ShareNoteSheet: View {

    let noteId: String

    @StateObject private var viewModel = ShareNoteViewModel()

    private var sheet: ShareSheet { ShareSheet.shared }

    var body: some View {
        let state = viewModel.state
        NavigationStack {
            List {
                Section {
                    Text("People get their own shared version of this note.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                    Picker("Access", selection: Binding(get: { state.role }, set: { viewModel.choose($0) })) {
                        ForEach(state.roles, id: \.self) { role in
                            Text(sheet.label(role: role)).tag(role)
                        }
                    }
                    .pickerStyle(.segmented)
                    TextField("Search people", text: Binding(get: { state.query }, set: { viewModel.onQueryChange($0) }))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    ForEach(state.results, id: \.userId) { person in
                        Button {
                            viewModel.pick(person)
                        } label: {
                            VStack(alignment: .leading) {
                                Text(person.name)
                                if !person.handle.isEmpty {
                                    Text(person.handle).font(.caption).foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                }
                if !state.picked.isEmpty {
                    Section("Sharing with") {
                        ForEach(state.picked, id: \.userId) { person in
                            HStack {
                                Text(person.name)
                                Spacer()
                                Button {
                                    viewModel.unpick(person.userId)
                                } label: {
                                    Image(systemName: "xmark.circle.fill").foregroundColor(.secondary)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(Text("Remove"))
                            }
                        }
                    }
                }
                Section {
                    Button(state.busy ? "Sharing…" : "Share") { viewModel.send() }
                        .disabled(!state.canSend)
                    if let error = state.error {
                        Text(error)
                            .font(.footnote)
                            .foregroundColor(.red)
                            .onTapGesture { viewModel.clearError() }
                    }
                    if state.sent {
                        Text("Shared. They will see it in their notes.")
                            .font(.footnote)
                            .foregroundColor(.accentColor)
                    }
                }
                ForEach(state.shares, id: \.shareId) { share in
                    shareSection(share, roles: state.roles)
                }
            }
            .navigationTitle("Share a copy")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onAppear { viewModel.open(noteId: noteId) }
    }

    @ViewBuilder
    private func shareSection(_ share: NoteShare, roles: [NoteRole]) -> some View {
        let manage = sheet.canManage(share: share)
        Section("Shared copy · \(share.members.count)") {
            ForEach(share.members, id: \.userId) { member in
                HStack {
                    Text(member.displayName())
                    Spacer()
                    Menu(sheet.label(role: member.access)) {
                        ForEach(roles, id: \.self) { role in
                            Button(sheet.label(role: role)) {
                                viewModel.changeRole(shareId: share.shareId, userId: member.userId, role: role)
                            }
                        }
                    }
                    .disabled(!manage)
                    if manage {
                        Button {
                            viewModel.remove(shareId: share.shareId, userId: member.userId)
                        } label: {
                            Image(systemName: "person.fill.xmark").foregroundColor(.red)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text("Remove access"))
                    }
                }
            }
            if manage {
                Button("Stop sharing", role: .destructive) { viewModel.stop(shareId: share.shareId) }
            }
        }
    }
}
