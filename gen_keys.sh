#!/bin/bash

set -e

echo "INFO compiling"
mvn --quiet clean package -DskipTests -am -pl=esgen
echo "INFO running cli"
java -jar ./esgen/target/esgen-jar-with-dependencies.jar "$@"