#!/bin/bash
SCRIPT=$(readlink -f "$0")
# echo "\n\nscript: ${SCRIPT}\n\n"

SCRIPTPATH=$(dirname "$SCRIPT")
# echo "\n\nscript path: ${SCRIPTPATH}\n\n"

cd "${SCRIPTPATH}" || (echo "cd to SCRIPTPATH failed. SCRIPTPATH: '${SCRIPTPATH}'" && exit)

while true; do
	# git operations
	git fetch
	git pull

	# maven rebuild and restart application
	mvn -U clean package spring-boot:run

	sleep 1m
done
