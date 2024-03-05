#!/bin/bash

set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

>&2 echo "INFO compiling"
>&2 ./mvnw --quiet package -DskipTests -f "$SCRIPT_DIR" -am -pl=ehealthid-cli
>&2 echo "INFO running cli"
java -jar "$SCRIPT_DIR/ehealthid-cli/target/ehealthcli.jar" "$@"