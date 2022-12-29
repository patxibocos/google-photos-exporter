#!/bin/sh -l

command="java -jar /app/google-photos-exporter.jar $1"
if [ -n "$2" ]; then
  command="$command -it $2"
fi
if [ -n "$3" ]; then
  command="$command -pp $3"
fi
if [ -n "$4" ]; then
  command="$command -mcs $4"
fi
if [ -n "$5" ]; then
  command="$command -oi $5"
fi

echo "Running: $command"
$command