#!/bin/bash

set -e

echo "INFO compiling"
mvn --quiet clean package -DskipTests -am -pl=ehealthid-cli
echo "INFO running cli"
java -jar ./ehealthid-cli/target/ehealthidcli.jar "$@"