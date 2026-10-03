import SwiftUI
import shared

// 🔧 15-Jul-2026 iOS parity (summary query): StaggeredGrid needs Identifiable items;
//   NoteSummary already exposes `id: String`, so the conformance is free.
extension NoteSummary: Identifiable {}

// 🔧 15-Jul-2026 iOS parity (summary query): the card renders a NoteSummary (was a full Note
//   whose contents it never used). Top to bottom — Android NoteCardView parity:
//   • title + updated date (unchanged look)
//   • the text snippet when the note HAS text…
//   • …otherwise media/doc COUNT BADGES ("3 photos · 1 audio") — media/doc-only notes are
//     first-class, not blank cards
//   • a thumbnail strip when the note has a visual media block
//   Usage: NoteBookView(summary: summary) { onOpen(summary) }
struct NoteBookView : View {
    let summary: NoteSummary
    let onClick: () -> Void

    var body: some View {
        GeometryReader{ geo in
            let width = geo.size.width
            let height = geo.size.height
            ZStack{
                VStack(alignment: .leading, spacing: 6){
                    // 🎨 20-Jul-2026 — serif card title on ivory (spec §3 card title 14.5/18, §5 note card)
                    Text(summary.title?.isEmpty == false ? summary.title! : "No Title ?")
                        .font(Theme.Fonts.cardTitle)
                        .foregroundColor(Theme.Colors.text)
                        .lineLimit(2)
                        .padding(.top, 24)
                        .padding(.horizontal, 12)
                    if let snippet = summary.snippet, !snippet.isEmpty {
                        VStack(alignment: .leading,){
                        Text(snippet)
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(Theme.Colors.text2) // 🎨 secondary text (spec §2.2)
                            .lineLimit(4)
                            .padding(.horizontal, 12)
                        mediaCountBadges
                            .foregroundColor(Theme.Colors.text2) // 🎨 count badges read as secondary text
                            .padding(.horizontal, 12)
                        }
                    } else {
                        mediaCountBadges
                            .foregroundColor(Theme.Colors.text2) // 🎨 count badges read as secondary text
                            .padding(.horizontal, 12)
                    }
                    if let thumb = summary.thumbnailPath, !thumb.isEmpty,
                       let image = UIImage(contentsOfFile: thumb) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                            .frame(width: width - 16, height: (height/2) - 8)
                            .cornerRadius(6)
                            .padding(.horizontal, 8)
                    }
                    Spacer(minLength: 8)
                }
                .frame(maxWidth: width, maxHeight: height, alignment: .topLeading)
                HStack{
                    Spacer()
                    // 🎨 20-Jul-2026 — meta date as tertiary text (spec §2.2 text-3), no heavy gray chip
                    Text("\(summary.shared ? "Shared · " : "")\(summary.updatedAt?.toLocalFormat(showTime: false) ?? "")")
                        .font(Theme.Fonts.metaCaption)
                        .foregroundColor(Theme.Colors.text3)
                        .padding(.top, 8)
                        .padding(.trailing, 10)
                }
                .frame(width :width,
                        height : height,
                        alignment: .topTrailing)
                // 🎨 20-Jul-2026 — copper fold corner = the ONLY loud accent on the card (spec §5 manuscript fold motif)
                // 🐛 25-Jul-2026 TAP FIX: this full-frame overlay was on top of the ZStack and swallowed
                //   the card's tap (the note wouldn't open). It's pure decoration — opt it out of hit-testing.
                foldCorner
                    .frame(width: width, height: height, alignment: .topTrailing)
                    .allowsHitTesting(false)
            }
            // 🎨 20-Jul-2026 — card is an IVORY surface with a hairline border (spec §5 note card / prototype .ncard),
            //   NOT a solid accent fill. This fixes the "wall of orange" — primary/saffron is an accent, not a card bg.
            .background(Theme.Colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: 14)) // spec §4 radius md (cards)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Theme.Colors.border, lineWidth: 1)
                )
                .shadow(color: Theme.Elevation.card, radius: Theme.Elevation.cardRadius, x: 0, y: Theme.Elevation.cardY)
                // 🐛 25-Jul-2026 TAP FIX: make the WHOLE card rect tappable (not just the opaque areas)
                .contentShape(Rectangle())
                .onTapGesture {
                    onClick()
                }
        }
    }

    // 🎨 20-Jul-2026 — copper triangle fold in the top-right (spec §5 "corner fold" manuscript motif).
    private var foldCorner: some View {
        Path { p in
            let s: CGFloat = 20
            p.move(to: CGPoint(x: -s, y: 0))
            p.addLine(to: CGPoint(x: 0, y: 0))
            p.addLine(to: CGPoint(x: 0, y: s))
            p.closeSubpath()
        }
        .fill(Theme.Colors.saffron.opacity(0.9))
    }

    // 🔧 15-Jul-2026 iOS parity: compact "what's inside" row for notes without text.
    private var mediaCountBadges: some View {
        HStack(spacing: 10) {
            countBadge(systemName: "photo", count: Int(summary.imageCount))
            countBadge(systemName: "video.fill", count: Int(summary.videoCount))
            countBadge(systemName: "mic.fill", count: Int(summary.audioCount))
            countBadge(systemName: "doc.fill", count: Int(summary.docCount))
        }
    }

    @ViewBuilder
    private func countBadge(systemName: String, count: Int) -> some View {
        if count > 0 {
            let displayCount = count > 1000 ? "\(count / 1000)k" : "\(count)"
            HStack(spacing: 2) {
                Image(systemName: systemName).font(.system(size: 11))
                Text(displayCount).font(.system(size: 12, weight: .medium))
            }
        }
    }
}

#Preview {
    NoteBookView(
        summary: NoteSummary(
            id: "12345",
            title: "Hello World.",
            categoryId: "",
            createdAt: "24/03/2025",
            updatedAt: "24/03/2025",
            snippet: nil,
            contentCount: 4,
            imageCount: 3,
            videoCount: 0,
            audioCount: 1,
            docCount: 0,
            thumbnailPath: nil,
            shared: false
        ),
             onClick: {
})
}
