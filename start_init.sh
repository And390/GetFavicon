#!/bin/bash
cd "$(dirname "$(realpath "$0")")";

while !(mysqladmin ping)
do
   sleep 3
done
./start.sh