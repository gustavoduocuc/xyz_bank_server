#!/usr/bin/env bash
# Pushes the locally built xyz-bank/<service>:<TAG> images to Amazon ECR.
# Creates nothing in AWS: the repositories xyz-bank/<service> must already exist.
#
#   AWS_ACCOUNT_ID=123456789012 AWS_REGION=us-east-1 TAG=1.0.0 ./scripts/push-images-to-ecr.sh
#
# Build first with `docker compose build` (use `--platform linux/amd64`, the task definitions
# run on X86_64 Fargate). TAG defaults to "latest".
set -euo pipefail

: "${AWS_ACCOUNT_ID:?set AWS_ACCOUNT_ID to the AWS account that owns the ECR repositories}"
: "${AWS_REGION:?set AWS_REGION to the region of the ECR repositories}"
TAG="${TAG:-latest}"
REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

SERVICES=(
  config-server eureka-server api-gateway core-service customers-service payments-service
  interests-service auth-server bff-web bff-mobile bff-atm data-migration
)

command -v aws >/dev/null || { echo "The AWS CLI is required" >&2; exit 1; }

aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"

for service in "${SERVICES[@]}"; do
  docker tag "xyz-bank/${service}:${TAG}" "${REGISTRY}/xyz-bank/${service}:${TAG}"
  docker push "${REGISTRY}/xyz-bank/${service}:${TAG}"
done
