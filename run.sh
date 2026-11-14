#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

VERSION=1.0.0
JAR="target/short-link-service-${VERSION}.jar"

if [[ ! -f "$JAR" ]]; then
  echo ">>> jar не найден — собираю (тесты пропускаю, полный прогон: mvn verify)"
  mvn -q -DskipTests package
fi

exec java -Dfile.encoding=UTF-8 -jar "$JAR" "$@"
