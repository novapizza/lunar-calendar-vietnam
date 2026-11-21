# iOS app (planned)

Swift + SwiftUI, with WidgetKit widgets. Not started: the Xcode project is created on a Mac.

Reads from `shared/`:
- `shared/data/*.json`: bundle as resources (or generate Swift constants) instead of re-typing names, holidays, nạp âm.
- `shared/design/tokens.json`: generate an asset catalog or a `Color` extension with the base and per-element palettes (light/dark).
- `shared/design/icon.svg`: app icon source. iOS alternate app icons (one per element) are possible; each must be bundled.
- `shared/test-vectors/*.json`: XCTest cases; the Swift lunar conversion must reproduce them exactly.
