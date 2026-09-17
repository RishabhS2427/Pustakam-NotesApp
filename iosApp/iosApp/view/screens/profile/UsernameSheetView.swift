import SwiftUI

/// 🆔 Choosing a handle.
///
/// 🔧 17-Sep-2026 — this takes the ViewModel as an @ObservedObject rather than a bagful of plain
///   values. A `.sheet` content closure is evaluated by the presenting view, and a value captured
///   there does not reliably re-read when the object behind it publishes — which showed up as the
///   Save button being permanently disabled no matter what you typed. Observing the object directly
///   means the sheet re-renders on its own and cannot hold a stale `canSave`.
struct UsernameSheetView: View {

    @ObservedObject var viewModel: ProfileViewModel

    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.md) {

            Text("Choose a username")
                .font(Theme.Fonts.sectionTitle)
                .foregroundStyle(palette.text)

            Text("This is how people find you. Letters, numbers and single hyphens, 3–30 characters.")
                .font(Theme.Fonts.metaCaption)
                .foregroundStyle(palette.text3)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 4) {
                Text("@")
                    .font(Theme.Fonts.body2)
                    .foregroundStyle(palette.text3)
                // No autocapitalise: a username is canonicalised to lowercase anyway
                TextField("username", text: $viewModel.draftUsername)
                    .font(Theme.Fonts.body2)
                    .foregroundStyle(palette.text)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .submitLabel(.done)
                    .onChange(of: viewModel.draftUsername) { _, newValue in
                        viewModel.onUsernameChange(newValue)
                    }
            }
            .padding(.horizontal, Theme.Spacing.lg)
            .padding(.vertical, Theme.Spacing.md)
            .background(palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Radius.md)
                    .stroke(hintColor, lineWidth: 1)
            )

            if !viewModel.usernameHint.isEmpty {
                Text(viewModel.usernameHint)
                    .font(Theme.Fonts.metaCaption)
                    .foregroundStyle(hintColor)
            }

            if !viewModel.suggestions.isEmpty {
                Text("Try instead")
                    .font(Theme.Fonts.metaCaption)
                    .foregroundStyle(palette.text3)
                HStack(spacing: Theme.Spacing.sm) {
                    ForEach(viewModel.suggestions.prefix(3), id: \.self) { suggestion in
                        Text("@\(suggestion)")
                            .font(Theme.Fonts.metaCaption)
                            .foregroundStyle(palette.text)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Theme.Colors.sand.opacity(0.4))
                            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.sm))
                            .onTapGesture { viewModel.onUsernameChange(suggestion) }
                    }
                }
            }

            // 🔧 the screen's error line sits BEHIND this sheet, so a failed check or a rejected
            //   claim used to leave the user staring at a dead button with no explanation.
            if let error = viewModel.error, !error.isEmpty {
                Text(error)
                    .font(Theme.Fonts.metaCaption)
                    .foregroundStyle(Theme.Colors.error)
            }

            Button(action: { viewModel.saveUsername() }) {
                Text("Save")
                    .font(Theme.Fonts.body2)
                    .foregroundStyle(Theme.Colors.ivory)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Theme.Spacing.md)
                    .background(viewModel.canSaveUsername ? palette.accent : palette.border)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.md))
            }
            .disabled(!viewModel.canSaveUsername)
        }
        .padding(.horizontal, Theme.Spacing.gutter)
        .padding(.top, Theme.Spacing.xxl)
        .padding(.bottom, Theme.Spacing.xxxl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.background.ignoresSafeArea())
    }

    // 🆔 "Checking…" is not a rejection, so it must not paint the field red
    private var hintColor: Color {
        if viewModel.usernameHint.isEmpty { return palette.border }
        if viewModel.usernameHintIsNeutral { return palette.text3 }
        return viewModel.isUsernameAvailable ? Theme.Colors.forest : Theme.Colors.error
    }
}
