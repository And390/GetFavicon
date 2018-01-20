#!/bin/sh
set -e
cd "$(dirname "$(realpath "$0")")"
. /work/secure/access/code/my-aruba-3/host.sh
#gradle distZip
#sshpass -p "$PASSWORD" ssh -p $PORT "$USER@$HOST" "timedatectl set-timezone Europe/Moscow; apt-get install unzip"   #for absolutely new server uncomment this, but you have to install java
#sshpass -p "$PASSWORD" ssh -p $PORT "$USER@$HOST" "mkdir -p /work/GetFavicon"  #uncomment these two lines and comment the thrid line below (with stop) to make initial deploy
#sshpass -p "$PASSWORD" scp -P $PORT -r config.properties "$USER@$HOST:/work/GetFavicon"
sshpass -p "$PASSWORD" ssh -p $PORT "$USER@$HOST" "/work/GetFavicon/stop.sh"
sshpass -p "$PASSWORD" scp -P $PORT build/distributions/GetFavicon.zip "$USER@$HOST:/work/"
sshpass -p "$PASSWORD" ssh -p $PORT "$USER@$HOST" "cd /work && unzip -o GetFavicon.zip -d . && GetFavicon/start.sh && tail -f -n 100 GetFavicon/log.txt"
