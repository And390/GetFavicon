#!/bin/bash
cd "$(dirname "$(realpath "$0")")";

SERVICE_NAME='GetFavicon Java server'
DO_WAIT_SHUTDOWN=true

if [ -f ".pid" ]; then
    APP_JVM_PID=`cat .pid`
    processexists=$(ps -p $APP_JVM_PID -o comm=)
    if [ -n "$processexists" ]; then
        echo "$SERVICE_NAME stoping ..."
        processexists=1

        if [[ -n $DO_WAIT_SHUTDOWN ]]; then
            SOFT_SHUTDOWN_CMD=$DO_WAIT_SHUTDOWN
            if [ "$DO_WAIT_SHUTDOWN" = true ]; then
                SOFT_SHUTDOWN_CMD="kill $APP_JVM_PID"
            fi
            echo $SOFT_SHUTDOWN_CMD
            eval $SOFT_SHUTDOWN_CMD
            shutdown_result=$?
            if [ $shutdown_result -eq 0 ]; then
                if [[ "$SHUTDOWN_SLEEP" =~ ^[0-9]+$ ]]; then
                    sleep $SHUTDOWN_SLEEP
                    if [ $? -ne 0 ]; then
                        exit 2
                    fi
                fi
                killcountdown=20
                while [ $killcountdown -gt 0 ] && [ -n "$processexists" ]; do
                    sleep 1
                    let killcountdown-=1
                    processexists=$(ps -p $APP_JVM_PID -o comm=)
                done
            else
                echo 'fail to execute graceful shutdown'
                if [ "$DO_WAIT_SHUTDOWN" != true ]; then
                    exit 2
                fi
            fi
        fi

        if [ -n "$processexists" ]; then
            if [[ -n $DO_WAIT_SHUTDOWN ]]; then
                echo "process doesn't stopped, send sigkill"
            fi
            kill -9 $APP_JVM_PID

            shutdown_result=$?
            if [ $shutdown_result -eq 0 ]; then
                killcountdown=10
                while [ $killcountdown -gt 0 ] && [ -n "$processexists" ]; do
                    sleep 1
                    let killcountdown-=1
                    processexists=$(ps -p $APP_JVM_PID -o comm=)
                done
            else
                echo 'fail to execute graceful shutdown'
                exit 2
            fi
        fi

        if [ -n "$processexists" ]; then
            echo "Can't kill process $APP_JVM_PID"
            exit 2
        else
            echo "$SERVICE_NAME stopped"
        fi
    else
        echo "Nothing to stop. Process not found"
    fi
    rm .pid
    exit 0
else
    echo "Nothing to stop. Process not found"
    exit 0
fi