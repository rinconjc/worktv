#!/bin/sh

type lein || {
    echo "downloading lein :)"
    curl https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein > ~/bin/lein
    chmod +x ~/bin/lein
}

~/bin/lein uberjar
