// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MiaoLibrary",
    platforms: [.iOS(.v16)],
    products: [.library(name: "MiaoLibrary", targets: ["MiaoLibrary"])],
    targets: [.target(name: "MiaoLibrary", path: "Sources/MiaoLibrary")]
)
