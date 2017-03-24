#!/bin/sh

[ -f ~/lein ] || {
    echo "downloading lein ... $HOME"
    type wget
    wget -v -S -O ~/lein "https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein"
    chmod +x ~/lein
}

~/lein -U clean uberjar
