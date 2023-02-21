#!/bin/sh -l

command="java -jar /app/exporter-cli.jar $1"
if [ -n "$2" ]; then
  command="$command -it $2"
fi
if [ -n "$3" ]; then
  command="$command -mcs $3"
fi
if [ -n "$4" ]; then
  command="$command -pp $4"
fi
if [ -n "$5" ]; then
  command="$command -oi $5"
fi
if [ -n "$6" ]; then
  command="$command -dpp $6"
fi
if [ -n "$7" ]; then
  command="$command -sfn $7"
fi
if [ -n "$8" ]; then
  command="$command -to $8"
fi
if [ -n "${9}" ]; then
  command="$command -rto ${10}"
fi
if [ -n "${10}" ]; then
  command="$command -oc ${11}"
fi

echo "Running: $command"
$command
