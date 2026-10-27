#!/bin/sh
# MathWizard launcher for Linux, macOS and Termux.
# First run: downloads the JLine jar (arrow-key menus) into lib/, then compiles and starts the game.
cd "$(dirname "$0")" || exit 1
JLINE_VERSION=3.26.3
JAR="lib/jline-$JLINE_VERSION.jar"
URL="https://repo1.maven.org/maven2/org/jline/jline/$JLINE_VERSION/jline-$JLINE_VERSION.jar"

if [ ! -f "$JAR" ]; then
  mkdir -p lib
  echo "Downloading JLine (needed for arrow-key menus) ..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --silent --show-error -o "$JAR" "$URL" || rm -f "$JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$JAR" "$URL" || rm -f "$JAR"
  else
    echo "curl or wget not found. Install one, or put the jar in lib/ by hand (see lib/README.txt)."
  fi
  [ -f "$JAR" ] || echo "JLine not available: the game still works, with numbered menus instead of arrow keys."
fi

CP="."
[ -f "$JAR" ] && CP=".:lib/*"
javac -encoding UTF-8 -cp "$CP" *.java && java --enable-native-access=ALL-UNNAMED -cp "$CP" Game
