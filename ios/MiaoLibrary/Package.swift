// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MiaoLibrary",
    platforms: [.iOS(.v16)],
    products: [
        .iOSApplication(
            name: "MiaoLibrary",
            targets: ["MiaoLibrary"]
        )
    ],
    targets: [
        .executableTarget(
            name: "MiaoLibrary",
            path: "Sources/MiaoLibrary"
        )
    ]
)
