#!/bin/bash

set -e

>&2 echo "INFO compiling"
>&2 ./mvnw --quiet clean package -DskipTests -am -pl=ehealthid-cli
>&2 echo "INFO running cli"
java -jar ./ehealthid-cli/target/ehealthcli.jar "$@"