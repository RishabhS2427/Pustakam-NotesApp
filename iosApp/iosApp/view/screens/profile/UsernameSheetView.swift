import SwiftUI

/// 🆔 Choosing a handle. Availability is checked before saving, always — which is what keeps a 409
/// meaningful, since baseApiCall discards the error body and cannot tell "taken" from "cooldown".
struct UsernameSheetView: View {

    @Binding var username: String
    let hint: String
    let isAvailable: Bool
    let isChecking: Bool
    let suggestions: [String]
    let canSave: Bool
    var onUsernameChange: (String) -> Void = { _ in }
    var onPickSuggestion: (String) -> Void = { _ in }
    var onSave: () -> Void = {}

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
                TextField("username", text: $username)
                    .font(Theme.Fonts.body2)
                    .foregroundStyle(palette.text)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .submitLabel(.done)
                    .onChange(of: username) { _, newValue in onUsernameChange(newValue) }
            }
            .padding(.horizontal, Theme.Spacing.lg)
            .padding(.vertical, Theme.Spacing.md)
            .background(palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Radius.md)
                    .stroke(hintColor, lineWidth: 1)
            )

            if !hint.isEmpty {
                Text(hint)
                    .font(Theme.Fonts.metaCaption)
                    .foregroundStyle(hintColor)
            }

            if !suggestions.isEmpty {
                Text("Try instead")
                    .font(Theme.Fonts.metaCaption)
                    .foregroundStyle(palette.text3)
                HStack(spacing: Theme.Spacing.sm) {
                    ForEach(suggestions.prefix(3), id: \.self) { suggestion in
                        Text("@\(suggestion)")
                            .font(Theme.Fonts.metaCaption)
                            .foregroundStyle(palette.text)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Theme.Colors.sand.opacity(0.4))
                            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.sm))
                            .onTapGesture { onPickSuggestion(suggestion) }
                    }
                }
            }

            Button(action: onSave) {
                Text("Save username")
                    .font(Theme.Fonts.body2)
                    .foregroundStyle(Theme.Colors.ivory)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Theme.Spacing.md)
                    .background(canSave ? palette.accent : palette.border)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.md))
            }
            .disabled(!canSave)
        }
        .padding(.horizontal, Theme.Spacing.gutter)
        .padding(.top, Theme.Spacing.xxl)
        .padding(.bottom, Theme.Spacing.xxxl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.background.ignoresSafeArea())
    }

    // 🆔 "Checking…" is not a rejection, so it must not paint the field red
    private var hintColor: Color {
        if hint.isEmpty { return palette.border }
        if isChecking { return palette.text3 }
        return isAvailable ? Theme.Colors.forest : Theme.Colors.error
    }
}
