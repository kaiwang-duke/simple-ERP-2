#!/bin/bash
set -e

# ==============================
# USAGE EXAMPLE:
#   ./deploy.sh -build cloud main-app
#   ./deploy.sh -build local db-switch-app
# ==============================

# ==============================
# CONFIGURATION
# ==============================
PROJECT_ID="eng-empire-470108-k1"
REGION="us-east1"
SERVICE_ACCOUNT="spring-boot-backend@eng-empire-470108-k1.iam.gserviceaccount.com"
CPU="1"
MEMORY="4Gi"
TIMEOUT="900"
SERVICE_NAME="erp01"

BUILD_MODE=""
APP_NAME=""

# Parse arguments
while [[ "$#" -gt 0 ]]; do
  case $1 in
    -build) BUILD_MODE="$2"; shift ;;
    -h|--help)
      echo "Usage: ./deploy.sh -build [cloud|local] [main-app|db-switch-app]"
      exit 0
      ;;
    *)
      APP_NAME="$1"
      ;;
  esac
  shift
done

# Validate arguments
if [ -z "$BUILD_MODE" ] || [ -z "$APP_NAME" ]; then
  echo "Usage: ./deploy.sh -build [cloud|local] [main-app|db-switch-app]"
  exit 1
fi

# ==============================
# UTILITY
# ==============================
display_message() {
    local header=$1
    local command=$2
    echo -e "\n=============================================="
    echo " $header"
    echo "=============================================="
    if [ -n "$command" ]; then
        echo -e "COMMAND: $command"
    fi
    echo "----------------------------------------------"
}

# ==============================
# STEP 1: Build Maven package
# ==============================
CMD="mvn -pl $APP_NAME clean package -DskipTests"
display_message "STEP 1: Building $APP_NAME JAR with Maven" "$CMD"
eval $CMD

# ==============================
# STEP 2: Build container (Cloud vs Local)
# ==============================
if [ "$BUILD_MODE" == "cloud" ]; then
  CMD="gcloud builds submit --tag gcr.io/${PROJECT_ID}/${APP_NAME} $APP_NAME"
  display_message "STEP 2: Building container in Cloud Build" "$CMD"
  eval $CMD
elif [ "$BUILD_MODE" == "local" ]; then
  CMD="docker build --platform linux/amd64 -t gcr.io/${PROJECT_ID}/${APP_NAME} $APP_NAME"
  display_message "STEP(local) 2.1: Building Docker image locally" "$CMD"
  eval $CMD

  CMD="docker tag gcr.io/${PROJECT_ID}/${APP_NAME} gcr.io/${PROJECT_ID}/${APP_NAME}:latest"
  display_message "STEP(local) 2.2: Tagging Docker image" "$CMD"
  eval $CMD

  CMD="docker push gcr.io/${PROJECT_ID}/${APP_NAME}:latest"
  display_message "STEP(local) 2.3 : Pushing Docker image to Google Container Registry" "$CMD"
  eval $CMD
else
  echo "❌ Invalid build mode: $BUILD_MODE (use 'cloud' or 'local')"
  exit 1
fi

# ==============================
# STEP 3: Deploy to Cloud Run
# ==============================
CMD="gcloud run deploy ${SERVICE_NAME} \
  --image gcr.io/${PROJECT_ID}/${APP_NAME}:latest \
  --platform managed \
  --region ${REGION} \
  --allow-unauthenticated \
  --service-account ${SERVICE_ACCOUNT} \
  --cpu ${CPU} \
  --memory ${MEMORY} \
  --timeout ${TIMEOUT} \
  --set-env-vars SPRING_PROFILES_ACTIVE=cloud"

display_message "STEP 3: Deploying $APP_NAME to Cloud Run" "$CMD"
eval $CMD

# ==============================
# DONE
# ==============================
display_message "✅ $APP_NAME successfully deployed using $BUILD_MODE build mode!" ""