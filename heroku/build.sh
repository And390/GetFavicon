#!/usr/bin/env bash
set -e
cd "$(dirname "$(dirname "$(readlink -f "$0")")")"

# create build dir
rm -rf out/heroku
mkdir out/heroku

# copy war
cp -r out/getfavicon.war out/heroku

# copy webapp-runner.jar and Procfile
cp -r heroku/Procfile out/heroku
cp /work/code/java/lib/webapp-runner-7.0.57.2.jar out/heroku/webapp-runner.jar

# copy it to Dropbox
rm -r /work/Dropbox/Apps/Heroku/geticon
cp -r out/heroku /work/Dropbox/Apps/Heroku/geticon
