/// Namespace for package-level metadata.
///
/// Deliberately not named `CloudmojiCore`: a type that shares its module's name
/// shadows the module for every client, so `CloudmojiCore.Category` resolves to
/// a member of the type and the module can no longer be named at all. That
/// matters here because `Category` also exists in `objc/runtime.h`, which
/// Foundation drags in — the app target needs to module-qualify ours to
/// disambiguate, and cannot if the module name is taken.
public enum CloudmojiCoreInfo {
    public static let version = "1.0.0"
}
