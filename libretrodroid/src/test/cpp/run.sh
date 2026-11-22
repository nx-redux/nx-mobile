#!/bin/sh
# Builds and runs every host test in this folder (clang on the development machine, nothing Android), or only the
# tests named: run.sh [name_test ...]. A test's build command is the lines indented by three spaces in the comment
# that opens its file, written to run from the repo root. A test's output is shown only when it fails.
cd "$(dirname "$0")/../../../.." || exit 1
log=$(mktemp)
status=0
for t in ${@:-$(cd libretrodroid/src/test/cpp && ls *_test.cpp | sed 's/\.cpp$//')}; do
	build=$(sed -n -e '/^\/\//!q' -e 's|^//   ||p' "libretrodroid/src/test/cpp/$t.cpp")
	if [ -n "$build" ] && sh -c "$build" > "$log" 2>&1; then
		echo "$t: pass"
	else
		echo "--- $t"
		cat "$log"
		status=1
	fi
done
rm -f "$log"
exit $status
