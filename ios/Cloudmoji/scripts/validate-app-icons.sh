#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

phone_manifest="$project_dir/Cloudmoji/Assets.xcassets/AppIcon.appiconset/Contents.json"
watch_manifest="$project_dir/CloudmojiWatch/Assets.xcassets/AppIcon.appiconset/Contents.json"

fail() {
    echo "App icon validation failed: $*" >&2
    exit 1
}

validate_source_icon() {
    label=$1
    manifest=$2
    directory=$(dirname -- "$manifest")
    filename=$(plutil -extract images.0.filename raw -o - "$manifest") \
        || fail "$label catalog has no filename"
    image="$directory/$filename"

    test -f "$image" || fail "$label icon is missing: $image"
    properties=$(sips -g pixelWidth -g pixelHeight -g hasAlpha "$image" 2>/dev/null)
    echo "$properties" | grep -q "pixelWidth: 1024" \
        || fail "$label icon is not 1024px wide"
    echo "$properties" | grep -q "pixelHeight: 1024" \
        || fail "$label icon is not 1024px high"
    echo "$properties" | grep -q "hasAlpha: no" \
        || fail "$label icon contains transparency"
}

validate_source_icon "iPhone/iPad" "$phone_manifest"
validate_source_icon "Apple Watch" "$watch_manifest"

# Pass a built Cloudmoji.app path to also prove that Xcode emitted the iPhone,
# iPad, and embedded Watch icon metadata and assets.
if [ "$#" -gt 0 ]; then
    app=$1
    test -d "$app" || fail "built app does not exist: $app"

    ls "$app"/AppIcon60x60@*.png >/dev/null 2>&1 \
        || fail "built iPhone icon is missing"
    ls "$app"/AppIcon76x76@*ipad.png >/dev/null 2>&1 \
        || fail "built iPad icon is missing"

    watch_app="$app/Watch/CloudmojiWatch.app"
    test -f "$watch_app/Assets.car" || fail "built Watch asset catalog is missing"
    watch_icon_name=$(plutil -extract \
        CFBundleIcons.CFBundlePrimaryIcon.CFBundleIconName \
        raw -o - "$watch_app/Info.plist") \
        || fail "built Watch app has no primary icon name"
    test "$watch_icon_name" = "AppIcon" \
        || fail "built Watch app uses '$watch_icon_name' instead of AppIcon"
fi

echo "App icons are valid for iPhone, iPad, and Apple Watch."
