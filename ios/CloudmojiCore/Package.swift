// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "CloudmojiCore",
    // macOS is listed only so `swift test` runs on the command line.
    platforms: [.iOS(.v17), .watchOS(.v10), .macOS(.v14)],
    products: [
        .library(name: "CloudmojiCore", targets: ["CloudmojiCore"])
    ],
    targets: [
        .target(
            name: "CloudmojiCore",
            resources: [.process("Resources")]
        ),
        .testTarget(
            name: "CloudmojiCoreTests",
            dependencies: ["CloudmojiCore"]
        )
    ]
)
