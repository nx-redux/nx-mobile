#!/bin/sh
# nx-mobile - netplay engine, link, DS link and shared TCP host tests. GPL-3.0-or-later.
# Run through `make -C libretrodroid/src/test/native/netplay test` (it builds out/ first).
# Logs and failed-check details go to out/<test>.log, shown only on failure.
cd "$(dirname "$0")"
status=0
for t in test_tcp test_engine test_link test_ndl test_psl; do
	if ./out/$t 2> out/$t.log; then
		echo "$t: ALL PASS"
	else
		echo "--- out/$t.log"
		cat out/$t.log
		status=1
	fi
done
exit $status
