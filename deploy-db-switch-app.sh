#!/bin/bash
set -euo pipefail

PROJECT_ID="eng-empire-470108-k1"
REGION="${REGION:-us-east1}"
SERVICE_ACCOUNT="spring-boot-backend@eng-empire-470108-k1.iam.gserviceaccount.com"
CPU="${CPU:-1}"
MEMORY="${MEMORY:-4Gi}"
TIMEOUT="${TIMEOUT:-900}"
SERVICE_NAME="${SERVICE_NAME:-db-switch-app}"
IMAGE_REF="gcr.io/${PROJECT_ID}/db-switch-app:latest"
BUILD_MODE="${1:-cloud}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    echo "Usage: ./deploy-db-switch-app.sh [cloud|local]"
}

display_message() {
    local header="$1"
    local command="$2"
    echo
    echo "=============================================="
    echo " $header"
    echo "=============================================="
    if [ -n "$command" ]; then
        echo "COMMAND: $command"
    fi
    echo "----------------------------------------------"
}

command_string() {
    printf '%q ' "$@"
}

run_cmd() {
    local header="$1"
    shift
    local cmd
    cmd="$(command_string "$@")"
    display_message "$header" "$cmd"
    "$@"
}

case "$BUILD_MODE" in
    cloud|local) ;;
    -h|--help)
        usage
        exit 0
        ;;
    *)
        usage
        exit 1
        ;;
esac

cd "$REPO_ROOT"


run_cmd "STEP 1: Building db-switch-app JAR with Maven" \
    mvn -pl db-switch-app -am clean package -DskipTests




if [ "$BUILD_MODE" = "cloud" ]; then
    run_cmd "STEP 2: Building container in Cloud Build" \
        gcloud builds submit --tag "$IMAGE_REF" db-switch-app
else
    run_cmd "STEP 2.1: Building Docker image locally" \
        docker build --platform linux/amd64 -t "$IMAGE_REF" db-switch-app
    run_cmd "STEP 2.2: Pushing Docker image" \
        docker push "$IMAGE_REF"
fi



run_cmd "STEP 3: Deploying db-switch-app to Cloud Run" \
    gcloud run deploy "$SERVICE_NAME" \
    --image "$IMAGE_REF" \
    --platform managed \
    --region "$REGION" \
    --allow-unauthenticated \
    --service-account "$SERVICE_ACCOUNT" \
    --cpu "$CPU" \
    --memory "$MEMORY" \
    --timeout "$TIMEOUT" \
    --set-env-vars SPRING_PROFILES_ACTIVE=cloud

display_message "Deployment successful" "service=${SERVICE_NAME}"
