#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/classes
javac -cp 'lib/*:.' -d build/classes @pas-stealth.srcs @pokePA.srcs @tetris.srcs
printf '%s\n' 'Compiled StealthAgent, TreeTraversalAgent, and TetrisQAgent into build/classes.'
