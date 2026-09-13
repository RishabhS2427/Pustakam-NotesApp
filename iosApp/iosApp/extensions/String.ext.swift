import shared

extension String {
    func toLocalFormat(showTime: Bool = true) -> String {
        let timeZone = Kotlinx_datetimeTimeZone.companion.currentSystemDefault()
        return String_extKt.toLocalFormat(self, timeZone: timeZone, showTime: showTime)
    }
}

extension Optional where Wrapped == String {
    /// 🖼️ 31-Aug-2026 — avatarUrl is stored RELATIVE, because the base URL is a tunnel that
    ///   changes. Same shared helper Android calls, so the two platforms cannot drift.
    var absoluteMediaUrl: String? { NetworkClientKt.toAbsoluteMediaUrl(self) }
}
