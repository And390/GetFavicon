#!/bin/sh
cd "$(dirname "$(realpath "$0")")"
gradle distZip
sshpass -p 'NwxFmX5b' ssh root@80.211.225.242 "/work/GetFavicon/stop.sh"
sshpass -p 'NwxFmX5b' scp build/distributions/GetFavicon.zip "root@80.211.225.242:/work/"
sshpass -p 'NwxFmX5b' ssh root@80.211.225.242 "cd /work && unzip -o GetFavicon.zip -d . && GetFavicon/start.sh && tail -f -n 100 GetFavicon/log.txt"
