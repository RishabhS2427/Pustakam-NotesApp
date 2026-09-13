
import SwiftUI

struct AvatarImageView: View {
    var imageUrl : String
    var size : CGFloat = 200
    var showsEditBadge : Bool = true
    var actionEdit : () -> Void = {}
    var actionClick : () -> Void = {}

    /// 🔧 31-Aug-2026 — this used URL(fileURLWithPath:) unconditionally, which turns an https
    ///   string into file:///…https:/… and always falls through to the placeholder. That is why a
    ///   remote avatar never rendered. A value with a scheme is a real URL; anything else is still
    ///   treated as a local path, which the notes pipeline relies on.
    private var resolvedURL: URL? {
        guard !imageUrl.isEmpty else { return nil }
        if let parsed = URL(string: imageUrl), parsed.scheme != nil { return parsed }
        return URL(fileURLWithPath: imageUrl)
    }

    var body: some View {
        
        ZStack (alignment: .bottom){
            if resolvedURL == nil {
                placeholderAvatar
            } else {
                AsyncImage(url: resolvedURL) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFit()
                            .frame(width: size, height: size)
                            .clipShape(Circle())
                            .overlay {
                                Circle().stroke(.brown, lineWidth: 4)
                            }
                            .shadow(radius:7)
                    } else if phase.error != nil {
                        placeholderAvatar
                    } else {
                        ProgressView()
                            .frame(width: size, height: size)
                    }
                }
            }
            if showsEditBadge {
                Image(systemName: "square.and.arrow.up.circle.fill")
                    .font(.system(size: 30).weight(.bold))
                    .scaledToFill().imageScale(.large)
                    .foregroundColor(.brown)
                    .frame(width: size * 0.75, height: 50, alignment: .bottomTrailing)
                    .padding(8)
                    .onTapGesture { actionEdit() }
            }
        }

    }
    
    private var placeholderAvatar: some View {
        Image(systemName: "person.crop.circle.fill")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .foregroundStyle(.brown)
            .clipShape(Circle())
            .overlay {
                Circle().stroke(.brown, lineWidth: 4)
            }
            .shadow(radius:7)
    }
    
    }
#Preview {
    AvatarImageView(imageUrl: "" ,actionEdit:  {print("ok")}, actionClick: {})
}
