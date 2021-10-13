#!/bin/bash
SCRIPT=$(readlink -f "$0")
SCRIPTPATH=$(dirname "$SCRIPT")

cd $SCRIPTPATH || (echo "cd to SCRIPTPATH failed. SCRIPTPATH: '$SCRIPTPATH'" && exit)

# git operations
git fetch
git pull

# maven rebuild and restart application
mvn -U clean package spring-boot:run