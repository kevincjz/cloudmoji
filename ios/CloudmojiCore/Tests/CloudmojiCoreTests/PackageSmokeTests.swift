import Testing
@testable import CloudmojiCore

@Test("package exposes its version")
func packageVersion() {
    #expect(CloudmojiCoreInfo.version == "1.0.0")
}
