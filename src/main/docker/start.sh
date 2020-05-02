#!/usr/bin/env sh

[ -z "$JAVA_XMX" ] && JAVA_XMX="256M"

[ -z "$DATABASE_HOST" ] && DATABASE_HOST="postgresql"
[ -z "$DATABASE_PORT" ] && DATABASE_PORT="5432"
[ -z "$DATABASE_NAME" ] && DATABASE_NAME="docker"
[ -z "$DATABASE_USERNAME" ] && DATABASE_USERNAME="docker"
[ -z "$DATABASE_PASSWORD" ] && DATABASE_PASSWORD="docker"
[ -z "$MAX_CONTENT_LENGTH" ] && MAX_CONTENT_LENGTH="1073741824"

JAVA_OPTS="-Xmx$JAVA_XMX -Xms$JAVA_XMX"

APP_OPTS="-Dapplication.grpc-port=9091 -Dapplication.port=9090"

if [ "$CUSTOM_CONFIG" = "" ]
then
    APP_OPTS="$APP_OPTS -Dakka.http.server.parsing.max-content-length=$MAX_CONTENT_LENGTH -Dakka.http.client.parsing.max-content-length=$MAX_CONTENT_LENGTH"
    APP_OPTS="$APP_OPTS -Ddatabase.jdbc-url=jdbc:postgresql://$DATABASE_HOST:$DATABASE_PORT/$DATABASE_NAME"
    APP_OPTS="$APP_OPTS -Ddatabase.username=$DATABASE_USERNAME -Ddatabase.password=$DATABASE_PASSWORD"

    if [ "$CLOUD_DRIVER" = "swarm" ]; then
        APP_OPTS="$APP_OPTS -Dcloud-driver.type=swarm"
        APP_OPTS="$APP_OPTS -Dcloud-driver.networkName=$NETWORK_NAME"
    elif [ "$CLOUD_DRIVER" = "ecs" ]; then
        META_DATA_URL=http://169.254.169.254/latest
        SIDECAR_HOST=$(wget -q -O - $META_DATA_URL/meta-data/local-ipv4)
        LOCALITY_ZONE=$(wget -q -O - $META_DATA_URL/meta-data/placement/availability-zone)
        INTERFACE=$(wget -q -O - $META_DATA_URL/meta-data/network/interfaces/macs/)
        [ -z "$ECS_DEPLOY_REGION" ] && ECS_DEPLOY_REGION=$(echo $LOCALITY_ZONE | sed 's/[a-z]$//')
        [ -z "$ECS_DEPLOY_ACCOUNT" ] && ECS_DEPLOY_ACCOUNT=$(wget -q -O - $META_DATA_URL/dynamic/instance-identity/document| jq -r ".accountId")
        [ -z "$ECS_VPC_ID" ] && ECS_VPC_ID=$(wget -q -O - $META_DATA_URL/meta-data/network/interfaces/macs/${INTERFACE}/vpc-id)
        [ -z "$ECS_DEPLOY_MEMORY_RESERVATION" ] && ECS_DEPLOY_MEMORY_RESERVATION="200"

        APP_OPTS="$APP_OPTS -Dcloud-driver.type=ecs"
        APP_OPTS="$APP_OPTS -Ddocker-repository.type=ecs"
        APP_OPTS="$APP_OPTS -Dcloud-driver.internal-domain-name=$ECS_INTERNAL_DOMAIN_NAME"
        APP_OPTS="$APP_OPTS -Dcloud-driver.region=$ECS_DEPLOY_REGION"
        APP_OPTS="$APP_OPTS -Dcloud-driver.cluster=$ECS_DEPLOY_CLUSTER"
        APP_OPTS="$APP_OPTS -Dcloud-driver.account-id=$ECS_DEPLOY_ACCOUNT"
        APP_OPTS="$APP_OPTS -Dcloud-driver.vpcId=$ECS_VPC_ID"
        APP_OPTS="$APP_OPTS -Dcloud-driver.memory-reservation=$ECS_DEPLOY_MEMORY_RESERVATION"
    elif [ "$CLOUD_DRIVER" = "kubernetes" ]; then
        APP_OPTS="$APP_OPTS -Dcloud-driver.type=kubernetes"
        APP_OPTS="$APP_OPTS -Dcloud-driver.proxy-host=$KUBE_PROXY_SERVICE_HOST"
        APP_OPTS="$APP_OPTS -Dcloud-driver.proxy-port=$KUBE_PROXY_SERVICE_PORT"
        APP_OPTS="$APP_OPTS -Dcloud-driver.kube-namespace=$KUBE_NAMESPACE"
        APP_OPTS="$APP_OPTS -Dcloud-driver.kube-registry-secret-name=$KUBE_REGISTRY_SECRET_NAME"
        APP_OPTS="$APP_OPTS -Ddocker-repository.type=remote"
        APP_OPTS="$APP_OPTS -Ddocker-repository.host=$REMOTE_DOCKER_REGISTRY_HOST"
        [ ! -z "$REMOTE_DOCKER_REGISTRY_USERNAME" ] && APP_OPTS="$APP_OPTS -Ddocker-repository.username=$REMOTE_DOCKER_REGISTRY_USERNAME"
        [ ! -z "$REMOTE_DOCKER_REGISTRY_PASSWORD" ] && APP_OPTS="$APP_OPTS -Ddocker-repository.password=$REMOTE_DOCKER_REGISTRY_PASSWORD"
        [ ! -z "$REMOTE_DOCKER_PULL_HOST" ] && APP_OPTS="$APP_OPTS -Ddocker-repository.pull-host=$REMOTE_DOCKER_PULL_HOST"
        [ ! -z "$REMOTE_DOCKER_IMAGE_PREFIX" ] && APP_OPTS="$APP_OPTS -Ddocker-repository.image-prefix=$REMOTE_DOCKER_IMAGE_PREFIX"
        APP_OPTS="$APP_OPTS"
    else
        APP_OPTS="$APP_OPTS -Dcloud-driver.type=docker"
        if [ ! -z "$NETWORK_NAME" ]; then
            APP_OPTS="$APP_OPTS -Dcloud-driver.network-name=$NETWORK_NAME"
        else
            APP_OPTS="$APP_OPTS -Dcloud-driver.network-name=bridge"
        fi
        APP_OPTS="$APP_OPTS -Ddocker-repository.type=local"
    fi

    echo "Custom config does not exist"
else
   APP_OPTS="$APP_OPTS -Dconfig.file=$CUSTOM_CONFIG"
fi

echo "Running Manager with:"
echo "JAVA_OPTS=$JAVA_OPTS"
echo "APP_OPTS=$APP_OPTS"

java $JAVA_OPTS $APP_OPTS -cp "/hydro-serving/app/manager.jar:/hydro-serving/app/lib/*" io.hydrosphere.serving.manager.Boot
