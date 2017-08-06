#!/bin/bash
cd "$(dirname "$(realpath "$0")")";

SERVICE_NAME='GetFavicon server'

./status.sh
if [ $? -ne 0 ]; then
    export JAVA_OPTS="-Dlog4j.configurationFile=log4j2.xml -Xss100m"
    nohup bin/GetFavicon 81 >>log.txt 2>&1 &
    if [ $? -ne 0 ]; then
        echo "Error starting $SERVICE_NAME"
        exit 1
    fi
    echo $! > .pid
    echo "$SERVICE_NAME started"
else
    echo "$SERVICE_NAME is already running"
fi