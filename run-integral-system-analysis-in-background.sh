#!/bin/sh
#Run in detached mode
screen -d -m ./run-integral-system-analysis.sh
#To view all background process: screen -ls
#To attach to a background process: screen -r ${processId}
#To detach from process: CTRL+A+D