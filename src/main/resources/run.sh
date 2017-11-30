#!/bin/bash

set -euo pipefail

jarFile=$1
shift
arguments=$*

java -jar $arguments $jarFile