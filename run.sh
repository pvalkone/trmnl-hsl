#!/bin/sh
#
# Launcher for the TRMNL HSL departures board server.
#
# Starts Babashka from this script's own directory so bb finds bb.edn
# AND the server finds its sibling .env. Both are resolved relative to
# cwd, and daemon(8) has no chdir flag.
#
# The rc.d service (trmnl_hsl) points daemon at this script, which
# keeps all shell quoting out of command_args.
#
cd "$(dirname "$0")" || exit 1
exec "${BB_BIN:-/usr/local/bin/bb}" serve
