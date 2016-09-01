#!/bin/bash

#Fail on non-zero
set -e

# Host port mapping for testing-app
hostport=8001

# Check if testing-app is running, if so, kill it
cid=$(sudo docker ps --filter="name=testing-app" -q -a)
if [ ! -z "$cid" ]
then
    sudo docker rm -f testing-app
fi

# Run the container, name it testing-app
testing_cid=$(sudo docker run -d --name testing-app -p $hostport:8000  $IMAGEID)
echo "testing_cid=$testing_cid" > props.env

# Get the container IP address, and run siege engine on it for 60 seconds
cip=$(sudo docker inspect --format '{{ .NetworkSettings.IPAddress }}' ${testing_cid})
sudo docker run --rm rufus/siege-engine  -b -t60S http://$cip:8000/ > output 2>&1

# Check service availability
avail=$(cat output | grep Availability | awk '{print $2}')
echo $avail
# shell uses = to compare strings, bash ==
if [ "$avail" = "100.00" ]
then
    echo "Availability high enough"
    sudo docker tag $IMAGEID ${DOCKER_USERNAME}/http-app:stable
    exit 0
else
    echo "Availability too low"
    exit 1
fi
