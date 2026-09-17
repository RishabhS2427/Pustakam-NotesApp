import SwiftUI
import shared

private let bioMaxLength = 160

/// 👤 Your own profile. Deliberately built from the SettingsSection / SettingsCard / SettingsRow
/// primitives that already exist — SettingsRow with isOn == nil is a chevron row and with a value
/// is a switch, which is exactly the shape an identity list and a privacy toggle need.
struct ProfileView: View {

    @Environment(\.palette) private var palette
    @StateObject private var viewModel = ProfileViewModel()
    // 👇 the Identity summary row jumps to the field that actually edits the name
    @FocusState private var nameFocused: Bool

    var onPickAvatar: () -> Void = {}

    var body: some View {
        ScrollView {
            VStack(alignment: .center, spacing: Theme.Spacing.xl) {

                // 🖼️ avatar, name and handle read as one block, so they keep their own tight spacing
                VStack(spacing: 6) {
                    AvatarImageView(
                        imageUrl: viewModel.avatarUrl ?? "",
                        size: 108,
                        showsEditBadge: true,
                        actionEdit: onPickAvatar,
                        actionClick: onPickAvatar
                    )
                    Text(viewModel.displayName)
                        .font(Theme.Fonts.displayL)
                        .foregroundStyle(palette.text)
                    Text(viewModel.handle.isEmpty ? "No username yet" : viewModel.handle)
                        .font(Theme.Fonts.bodyText)
                        .foregroundStyle(palette.accent)
                }
                .padding(.top, Theme.Spacing.sm)

                if let error = viewModel.error {
                    Text(error)
                        .font(Theme.Fonts.metaCaption)
                        .foregroundStyle(Theme.Colors.error)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }

                SettingsSection(title: "Identity") {
                    SettingsCard {
                        SettingsRow(icon: "at", tint: Theme.Colors.indigo,
                                    title: "Username",
                                    subtitle: "How people find you and start a chat",
                                    value: viewModel.handle.isEmpty ? "Choose one" : viewModel.handle,
                                    onTap: { viewModel.openUsernameEditor() })
                        SettingsRow(icon: "person.text.rectangle", tint: Theme.Colors.copper,
                                    title: "Display name",
                                    value: viewModel.draftName.isEmpty ? "Not set" : viewModel.draftName,
                                    isLast: true,
                                    onTap: { nameFocused = true })
                    }
                }

                SettingsSection(title: "About you") {
                    SettingsCard {
                        VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                            TextField("Name", text: $viewModel.draftName)
                                .textFieldStyle(OutlineTextfieldStyle())
                                .focused($nameFocused)

                            TextField("Bio", text: $viewModel.draftBio, axis: .vertical)
                                .lineLimit(3, reservesSpace: true)
                                .padding(Theme.Spacing.md)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(Theme.Colors.secondary, lineWidth: 1)
                                )
                                .onChange(of: viewModel.draftBio) { _, newValue in
                                    if newValue.count > bioMaxLength {
                                        viewModel.draftBio = String(newValue.prefix(bioMaxLength))
                                    }
                                }

                            Text("\(viewModel.draftBio.count)/\(bioMaxLength)")
                                .font(Theme.Fonts.metaCaption)
                                .foregroundStyle(palette.text3)

                            Button(action: { viewModel.saveDetails() }) {
                                Text(viewModel.isSaving ? "Saving…" : "Save")
                                    .font(Theme.Fonts.body2)
                                    .foregroundStyle(Theme.Colors.ivory)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, Theme.Spacing.md)
                                    .background(canSaveDetails ? palette.accent : palette.border)
                                    .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.md))
                            }
                            .disabled(!canSaveDetails)
                        }
                        .padding(Theme.Spacing.md)
                    }
                }

                SettingsSection(title: "Discovery") {
                    SettingsCard {
                        // 🔒 the copy must not overstate this: it governs SEARCH only
                        SettingsRow(icon: "eye", tint: Theme.Colors.forest,
                                    title: "Let people find me",
                                    subtitle: "Anyone with your exact username can always open your profile",
                                    isOn: viewModel.isDiscoverable,
                                    onToggle: { viewModel.setDiscoverable($0) },
                                    isLast: true)
                    }
                }
            }
            .padding(.horizontal, Theme.Spacing.gutter)
            .padding(.vertical, Theme.Spacing.lg)
        }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .task { viewModel.load() }
        // 🆔 a server-assigned handle opens the picker once, so nobody is stuck as reader-8f3k2q
        .onChange(of: viewModel.shouldPromptForUsername) { _, needsOne in
            if needsOne && !viewModel.isEditingUsername { viewModel.openUsernameEditor() }
        }
        .sheet(isPresented: $viewModel.isEditingUsername, onDismiss: { viewModel.closeUsernameEditor() }) {
            UsernameSheetView(viewModel: viewModel)
                .presentationDetents([.medium])
        }
    }

    private var canSaveDetails: Bool { viewModel.hasUnsavedDetails && !viewModel.isSaving }
}
