#!/bin/bash

# Define variables
PROJECT_ID="eng-empire-470108-k1"
IMAGE_NAME="erp01"
REGION="us-east1"
SERVICE_ACCOUNT="spring-boot-backend@eng-empire-470108-k1.iam.gserviceaccount.com"
CPU="1"
MEMORY="4Gi"
TIMEOUT="900"

# Function to display messages in a box
display_message() {
    local message=$1
    local command=$2
    local len=${#message}
    local border=$(printf '%0.s-' $(seq 1 $((len + 4))))

    echo ""
    echo " ${border}"
    echo "|  ${message}  |"
    echo " ${border}"
    if [ -n "$command" ]; then
        echo "|  ${command}  |"
        echo " ${border}"
    fi
    echo ""
}

# Function to handle errors
handle_error() {
    local message=$1
    display_message "ERROR: ${message}" ""
    exit 1
}

# Step 1: Clean and package the application
message="Cleaning and packaging the application..."
command="mvn clean package"
display_message "${message}" "${command}"
eval ${command} || handle_error "Maven build failed. Exiting..."

# Step 2: Build the Docker image
message="Building the Docker image..."
command="docker build -t gcr.io/${PROJECT_ID}/${IMAGE_NAME} ."
display_message "${message}" "${command}"
eval ${command} || handle_error "Docker build failed. Exiting..."

# Step 3: Push the Docker image to Google Container Registry
message="Building and pushing multi-arch image (linux/amd64,linux/arm64)..."
command="docker buildx build --platform linux/amd64,linux/arm64 \
	 -t gcr.io/${PROJECT_ID}/${IMAGE_NAME}:latest \
         --push ."
display_message "${message}" "${command}"
eval ${command} || handle_error "Docker push failed. Exiting..."

# Step 4: Deploy the image to Google Cloud Run
message="Deploying the application to Google Cloud Run..."
command="gcloud run deploy ${IMAGE_NAME} \
    --image gcr.io/${PROJECT_ID}/${IMAGE_NAME}:latest \
    --platform managed \
    --region ${REGION} \
    --allow-unauthenticated \
    --service-account ${SERVICE_ACCOUNT} \
    --cpu ${CPU} \
    --memory ${MEMORY} \
    --timeout ${TIMEOUT} \
    --set-env-vars SPRING_PROFILES_ACTIVE=cloud"

display_message "${message}" "${command}"
eval ${command} || handle_error "Deployment to Cloud Run failed. Exiting..."

display_message "Deployment successful!" ""