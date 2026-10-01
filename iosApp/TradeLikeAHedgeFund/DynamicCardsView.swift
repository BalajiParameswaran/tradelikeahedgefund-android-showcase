import SwiftUI

/// Reusable flip-card list for AI-generated study cards (Topics + Tutor).
struct DynamicCardsView: View {
    let cards: [DynamicCard]
    @State private var flipped: Set<UUID> = []

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(cards) { card in
                Button {
                    if flipped.contains(card.id) { flipped.remove(card.id) } else { flipped.insert(card.id) }
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(flipped.contains(card.id) ? "Back — tap to flip" : "Front — tap to flip")
                            .font(.caption).foregroundColor(.muted)
                        Text(flipped.contains(card.id) ? card.back : card.front)
                            .font(.body).foregroundColor(.ink)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.navySurface)
                    .cornerRadius(10)
                }
            }
        }
    }
}
