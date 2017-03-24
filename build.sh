#!/bin/sh

type lein || {
    echo "downloading lein ... $HOME"
    type wget
    wget -v -S -O ~/bin/lein "https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein"
    chmod +x ~/bin/lein
}

~/bin/lein uberjar
