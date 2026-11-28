// swift-tools-version:5.9
// LunarCore: the lunar calendar, day facts, mệnh and personal events of the iOS app, ported from
// apps/android/core (Kotlin). Pure Swift + Foundation, no UIKit, so `swift test` runs on a Mac
// without a simulator. The tests read shared/test-vectors/ straight from the repository.
import PackageDescription

let package = Package(
    name: "LunarCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "LunarCore", targets: ["LunarCore"]),
    ],
    targets: [
        .target(
            name: "LunarCore",
            // Copies of shared/data/*.json and shared/design/tokens.json, written by `npm run gen`
            // (shared/scripts/gen-ios-data.mjs); `npm run check` fails when they drift.
            // .process puts the JSON files at the bundle's root. With .copy("Resources")
            // (a Resources/ folder inside the bundle) the iOS Simulator build failed in
            // codesign: "bundle format unrecognized" (Xcode 26.6, 2026-11-27).
            resources: [.process("Resources")]
        ),
        .testTarget(name: "LunarCoreTests", dependencies: ["LunarCore"]),
    ]
)
