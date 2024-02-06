#!/bin/bash

mvn --quiet clean compile exec:java -Dexec.mainClass="com.oviva.gesundheitsid.esgen.Main" -f esgen