import SwiftUI
import shared

/// 💬 Timestamps and the typing dots. Kept together so the chat views stay about layout.
enum ChatTimeFormatter {

    private static let clockFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMM"
        return formatter
    }()

    static func clock(_ millis: Int64) -> String {
        guard millis > 0 else { return "" }
        return clockFormatter.string(from: date(millis))
    }

    /// "Today", "Yesterday", then "12 Aug" — the separator above a run of messages.
    static func dayLabel(_ millis: Int64) -> String {
        guard millis > 0 else { return "" }
        let day = date(millis)
        if Calendar.current.isDateInToday(day) { return "Today" }
        if Calendar.current.isDateInYesterday(day) { return "Yesterday" }
        return dayFormatter.string(from: day)
    }

    /// The inbox row's right-hand stamp: the clock today, the date before that.
    static func inboxStamp(_ millis: Int64) -> String {
        guard millis > 0 else { return "" }
        return Calendar.current.isDateInToday(date(millis)) ? clock(millis) : dayLabel(millis)
    }

    private static func date(_ millis: Int64) -> Date {
        Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
    }
}

/// The three dots under a name that is typing, or inside a half-written assistant reply.
struct TypingDotsView: View {
    @State private var phase = 0.0

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .frame(width: 6, height: 6)
                    .foregroundColor(Theme.Colors.text3)
                    .opacity(opacity(for: index))
            }
        }
        .padding(.vertical, 4)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { phase = 1 }
        }
    }

    private func opacity(for index: Int) -> Double {
        let offset = Double(index) * 0.2
        return 0.25 + 0.75 * abs(sin((phase + offset) * .pi))
    }
}

/// "Aarav is typing…" above the composer.
struct TypingIndicatorView: View {
    let names: [String]

    var body: some View {
        if !names.isEmpty {
            HStack(spacing: 6) {
                Text(names.count == 1 ? "\(names[0]) is typing" : "\(names.count) people are typing")
                    .font(.caption2)
                    .foregroundColor(Theme.Colors.text3)
                TypingDotsView()
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
        }
    }
}
