#!/bin/bash
cd "$(dirname "$(realpath "$0")")";

if [ -f ".pid" ]; then
    APP_JVM_PID=`cat .pid`
    processexists=$(ps -p $APP_JVM_PID -o comm=)
    if [ -n "$processexists" ]; then
        exit 0
    fi
fi

exit 3