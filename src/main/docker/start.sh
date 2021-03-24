#!/usr/bin/env sh
JAVA=$(which java)

[ -z $CUSTOM_CONFIG ] && CUSTOM_CONFIG="/app/config/application.conf"
APP_OPTS="$APP_OPTS -Dconfig.file=$CUSTOM_CONFIG"

echo ${JAVA} ${JAVA_OPTS} ${APP_OPTS} -cp "/app/manager.jar:/app/lib/*" io.hydrosphere.serving.manager.Boot
${JAVA} ${JAVA_OPTS} ${APP_OPTS} -cp "/app/manager.jar:/app/lib/*" io.hydrosphere.serving.manager.Boot
