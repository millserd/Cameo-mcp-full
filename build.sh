#!/usr/bin/env sh
# Builds the Cameo MCP plugin. Asks for the Cameo install dir unless CAMEO_HOME is set.
# Usage: ./build.sh [gradle tasks...]   (default: rdzip dist)
[ $# -eq 0 ] && set -- rdzip dist
cameo="$CAMEO_HOME"
while [ -z "$cameo" ] || [ ! -d "$cameo/lib" ]; do
    [ -n "$cameo" ] && echo "No 'lib' folder in '$cameo'."
    printf 'Cameo / CATIA Magic install directory: '
    read -r cameo || exit 1
done
exec gradle "$@" "-PcameoHome=$cameo"
