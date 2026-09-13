import SwiftUI

struct SettingsView: View {

    @Environment(ThemeManager.self) private var themeManager
    @Environment(\.palette) private var palette
    @Environment(Router.self) var router: Router
    @State private var readerPrefs = ReaderPrefsAdapter()
    @State private var readingMode: ReadingMode = .page
    @State private var authBridge = AuthBridgeAdapter()
    // 👤 31-Aug-2026 — the header used to be four hardcoded strings, one of them an email address
    @StateObject private var profile = ProfileViewModel()
    var body: some View {
        @Bindable var themeManager = themeManager

        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.xxl) {

                SettingsProfileHeader(
                    initial: profile.initial,
                    name: profile.user == nil ? "Your profile" : profile.displayName,
                    subtitle: profileSubtitle,
                    badge: profileBadge,
                    avatarUrl: profile.avatarUrl,
                    onTap: { router.navigate(to: .Profile) }
                )

                // 🎨 Appearance — the only live section
                SettingsSection(title: "Appearance") {
                    VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                        ThemeTilePicker(selection: $themeManager.mode)

                        Text(appearanceHint)
                            .font(Theme.Fonts.metaCaption)
                            .foregroundStyle(palette.text3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                SettingsSection(title: "Editor & Reading") {
                    SettingsCard {
                        SettingsRow(icon: "textformat", tint: Theme.Colors.saffron,
                                    title: "Editor font", subtitle: "Display serif · 16pt", value: "Iowan")
                        SettingsRow(icon: "keyboard", tint: Theme.Colors.forest,
                                    title: "Markdown shortcuts", subtitle: "Auto-format as you type", isOn: true)
                        SettingsRow(icon: "moonphase.waning.crescent", tint: Theme.Colors.copper,
                                    title: "Focus mode dimming", subtitle: "Fade inactive paragraphs", isOn: true)
                        // 📖 23-Jul-2026 — reader layout. Writes the SAME persisted value as the
                        //   reader's toolbar toggle, so changing it in either place updates the other.
                        SettingsRow(icon: "doc.plaintext", tint: Theme.Colors.gold,
                                    title: "Scrolling mode",
                                    subtitle: readingMode == .scroll
                                        ? "Documents scroll continuously"
                                        : "Documents turn like book pages",
                                    isOn: readingMode == .scroll,
                                    onToggle: { isOn in
                                        let next: ReadingMode = isOn ? .scroll : .page
                                        readingMode = next
                                        readerPrefs.setReadingMode(next)
                                    })
                        SettingsRow(icon: "clock.arrow.circlepath", tint: Theme.Colors.indigo,
                                    title: "Version history", subtitle: "Keep 30 days of snapshots",
                                    value: "", isLast: true)
                    }
                }

                SettingsSection(title: "Sync & Backup") {
                    SettingsCard {
                        SettingsRow(icon: "icloud.fill", tint: Theme.Colors.forest,
                                    title: "Cloud sync", subtitle: "Last synced 2 min ago · 1.2 GB", isOn: true)
                        SettingsRow(icon: "arrow.down.circle.fill", tint: Theme.Colors.saffron,
                                    title: "Auto backup", subtitle: "Daily · encrypted", isOn: true)
                        SettingsRow(icon: "arrow.left.arrow.right", tint: Theme.Colors.copper,
                                    title: "Offline mode", subtitle: "Keep all notes on device",
                                    isOn: false, isLast: true)
                    }
                }

                SettingsSection(title: "More") {
                    SettingsCard {
                        SettingsRow(icon: "globe", tint: Theme.Colors.indigo,
                                    title: "Language", value: "English")
                        SettingsRow(icon: "figure.wave", tint: Theme.Colors.gold,
                                    title: "Accessibility", value: "")
                        SettingsRow(icon: "lock.fill", tint: Theme.Colors.copper,
                                    title: "Privacy & Security", subtitle: "App lock · Face ID", value: "")
                        SettingsRow(icon: "gearshape.2.fill", tint: Theme.Colors.forest,
                                    title: "Developer options", value: "",)
                        SettingsRow(icon: "rectangle.portrait.and.arrow.right", tint: Color.red,
                                    title: "Logout ", value: "", onTap: { authBridge.logout {router.navigate(to: .Login)}})
                    }
                }
            }
            .padding(.horizontal, Theme.Spacing.gutter)
            .padding(.vertical, Theme.Spacing.lg)
        }
        .background(palette.background.ignoresSafeArea())
        // 📖 23-Jul-2026 — restore the persisted pick, and follow changes made in the reader
        .onAppear { readerPrefs.observeReadingMode { readingMode = $0 } }
        .task { profile.load() }
    }

    /// 🔒 The handle, never the email. Email and phone are login credentials from 31-Aug-2026 and
    /// the server no longer returns anyone else's; rendering our own here would still teach the
    /// wrong habit, and the handle is what a person actually shares.
    private var profileSubtitle: String {
        guard profile.user != nil else { return "Tap to set up" }
        return profile.handle.isEmpty ? "Tap to pick a username" : profile.handle
    }

    /// 🆔 the nudge replaces the badge until a real handle exists.
    private var profileBadge: String? {
        guard let user = profile.user else { return nil }
        return (user.username ?? "").isEmpty ? "SET UP" : nil
    }

    // 🎨 tells the user what the current pick actually does
    private var appearanceHint: String {
        switch themeManager.mode {
        case .system: return "Follows your iOS appearance, including scheduled Dark Mode."
        case .light:  return "Always uses the warm parchment palette."
        case .dark:   return "Always uses the warm dark-ink palette."
        case .amoled: return "True black surfaces — saves power on OLED displays."
        }
    }
}

// MARK: - Building blocks

// 🎨 22-Jul-2026 — uppercase group label + content, matching spec §6 grouping.
struct SettingsSection<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            Text(title.uppercased())
                .font(Theme.Fonts.metaCaption)
                .tracking(1.2)
                .foregroundStyle(palette.text3)
                .padding(.horizontal, Theme.Spacing.xs)
            content
        }
    }
}

// 🎨 22-Jul-2026 — rounded surface that hosts a run of SettingsRow.
struct SettingsCard<Content: View>: View {
    @ViewBuilder var content: Content
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) { content }
            .background(palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.md))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Radius.md)
                    .stroke(palette.border, lineWidth: 1)
            )
            .shadow(color: Theme.Elevation.card,
                    radius: Theme.Elevation.cardRadius, x: 0, y: Theme.Elevation.cardY)
    }
}

// 🎨 22-Jul-2026 — one settings row: tinted icon, title/subtitle, then a toggle OR a value chevron.
struct SettingsRow: View {
    let icon: String
    let tint: Color
    let title: String
    var subtitle: String? = nil
    var value: String? = nil
    var isOn: Bool? = nil
    // 📖 23-Jul-2026 — rows that own real state pass a callback; presentational rows omit it
    var onToggle: ((Bool) -> Void)? = nil
    var isLast: Bool = false
    var onTap: () -> Void  = {}
    @State private var toggleState: Bool = false
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: Theme.Spacing.md) {

                RoundedRectangle(cornerRadius: 9)
                    .fill(tint.opacity(0.16))
                    .frame(width: 32, height: 32)
                    .overlay(
                        Image(systemName: icon)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(tint)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(Theme.Fonts.bodyText)
                        .foregroundStyle(palette.text)
                    if let subtitle {
                        Text(subtitle)
                            .font(Theme.Fonts.metaCaption)
                            .foregroundStyle(palette.text3)
                    }
                }

                Spacer(minLength: Theme.Spacing.sm)
                if isOn != nil {
                    Toggle("", isOn: $toggleState)
                        .labelsHidden()
                        .tint(palette.accent)
                        // 📖 23-Jul-2026 — forward changes to the owner when the row is state-backed
                        .onChange(of: toggleState) { _, newValue in onToggle?(newValue) }
                } else {
                    HStack(spacing: 4) {
                        if let value, !value.isEmpty {
                            Text(value)
                                .font(Theme.Fonts.metaCaption)
                                .foregroundStyle(palette.text3)
                        }
                        Image(systemName: Images.arrowRight)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(palette.text3)
                    }
                }
            }
            .padding(.horizontal, Theme.Spacing.lg)
            .padding(.vertical, Theme.Spacing.md)

            if !isLast {
                Divider()
                    .overlay(palette.border)
                    .padding(.leading, 60)
            }
        }
        .contentShape(Rectangle())
        .onAppear { toggleState = isOn ?? false }
        .onTapGesture {
            onTap()
        }
        // 📖 23-Jul-2026 — follow the owner's value when it changes elsewhere (e.g. the reader's
        //   toolbar toggle). Assigning the same value doesn't re-fire onChange, so no write echo.
        .onChange(of: isOn) { _, newValue in
            if let newValue, newValue != toggleState { toggleState = newValue }
        }
    }
}

// 🎨 22-Jul-2026 — profile header (spec §6): gradient avatar, identity, PRO badge.
struct SettingsProfileHeader: View {

    let initial: String
    let name: String
    let subtitle: String
    let badge: String?
    var avatarUrl: String? = nil
    var onTap: () -> Void = {}

    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: Theme.Spacing.lg) {

            // 🖼️ the gradient initial is the placeholder, so a user with no picture still reads as themselves
            if let avatarUrl, !avatarUrl.isEmpty, let url = URL(string: avatarUrl) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        gradientInitial
                    }
                }
                .frame(width: 54, height: 54)
                .clipShape(Circle())
            } else {
                gradientInitial
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(name)
                    .font(Theme.Fonts.sectionTitle)
                    .foregroundStyle(palette.text)
                Text(subtitle)
                    .font(Theme.Fonts.metaCaption)
                    .foregroundStyle(palette.text3)
                    .lineLimit(1)
            }

            Spacer(minLength: Theme.Spacing.sm)

            if let badge {
                Text(badge)
                    .font(.system(size: 10, weight: .heavy))
                    .tracking(0.8)
                    .foregroundStyle(Color(hex: "#7E3F20"))
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(
                        LinearGradient(colors: [Theme.Colors.saffron, Theme.Colors.gold],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.sm))
            }
        }
        .padding(Theme.Spacing.lg)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Radius.lg)
                .stroke(palette.border, lineWidth: 1)
        )
        .shadow(color: Theme.Elevation.card,
                radius: Theme.Elevation.cardRadius, x: 0, y: Theme.Elevation.cardY)
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }

    private var gradientInitial: some View {
        Circle()
            .fill(LinearGradient(colors: [Theme.Colors.indigo, Theme.Colors.forest],
                                 startPoint: .topLeading, endPoint: .bottomTrailing))
            .frame(width: 54, height: 54)
            .overlay(
                Text(initial)
                    .font(Theme.Fonts.displayL)
                    .foregroundStyle(.white)
            )
    }
}

// MARK: - Previews (sample data)

#Preview("Settings — Light") {
    SettingsPreviewHost(mode: .light).preferredColorScheme(.light)
}

#Preview("Settings — Dark") {
    SettingsPreviewHost(mode: .dark).preferredColorScheme(.dark)
}

#Preview("Settings — AMOLED") {
    SettingsPreviewHost(mode: .amoled).preferredColorScheme(.dark)
}

// 🎨 22-Jul-2026 — preview host injects ThemeManager + palette like the app root does.
private struct SettingsPreviewHost: View {
    let mode: ThemeMode
    @State private var manager = ThemeManager()
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        SettingsView()
            .environment(manager)
            .environment(\.palette, ThemePalette(mode: mode, scheme: scheme))
            .onAppear { manager.setMode(mode) }
    }
}
