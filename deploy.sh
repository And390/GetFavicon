#!/bin/sh
cd "$(dirname "$(realpath "$0")")"
gradle distZip
sshpass -p 'mGOGKb6absnI' ssh root@212.109.219.152 "/var/www/GetFavicon/stop.sh"
sshpass -p 'mGOGKb6absnI' scp build/distributions/GetFavicon.zip "root@212.109.219.152:/var/www/"
sshpass -p 'mGOGKb6absnI' ssh root@212.109.219.152 "cd /var/www && unzip -o GetFavicon.zip -d . && GetFavicon/start.sh && tail -f -n 100 GetFavicon/log.txt"
