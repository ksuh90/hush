#!/bin/sh
# Checks an APK for the problems that stop a manual install.
# Usage: check-apk.sh <build-tools dir> <apk>
set -eu
tools=$1
apk=$2
fail() {
    echo "check-apk: $1" >&2
    exit 1
}

# With minSdk 24 or more, apksigner skips v1. --min-sdk-version 23 makes it check v1 too.
signers=$("$tools/apksigner" verify --verbose --min-sdk-version 23 "$apk" 2>/dev/null) ||
    fail "apksigner cannot verify $apk with v1 and v2 (the Samsung installer needs both)"
for scheme in "v1 scheme (JAR signing)" "v2 scheme (APK Signature Scheme v2)" "v3 scheme (APK Signature Scheme v3)"; do
    echo "$signers" | grep -q "Verified using $scheme: true" || fail "the APK is not signed with the $scheme"
done

"$tools/zipalign" -c -P 16 4 "$apk" || fail "the APK is not aligned (zipalign -P 16)"

badging=$("$tools/aapt2" dump badging "$apk")
echo "$badging" | grep -q "minSdkVersion:'33'" || fail "minSdkVersion is not 33"
echo "$badging" | grep -q "native-code: 'arm64-v8a'" || fail "native code is not arm64-v8a only"
if echo "$badging" | grep -q "testOnly"; then fail "the APK is test-only"; fi

echo "check-apk: OK $apk"
