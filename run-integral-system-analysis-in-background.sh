#!/bin/bash
if [ -z "$1" ]
then
   echo "No argument supplied. Usage: $0 <number of sessions>"
else
	for (( c=0; c<$1; c++ ))
	do 
		#Run in detached mode
		screen -d -m ./run-integral-system-analysis.sh
	done
fi
#List all background sessions
screen -ls;
echo "To resume a session use: screen -r <session id>"
echo "To kill session use: screen -X -S <session id> quit"
echo "To kill all sessions use: killall screen"
echo "To detach from a session use: CTRL+A+D"