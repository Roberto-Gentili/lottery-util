#!/bin/bash
for (( c=0; c<3; c++ ));
do 
	#Run in detached mode
	screen -d -m ./run-integral-system-analysis.sh;
done;
#List all background sessions
screen -ls;
#To kill a detached session: screen -X -S ${processId} quit
#To attach to a background session: screen -r ${processId}
#To detach from process: CTRL+A+D