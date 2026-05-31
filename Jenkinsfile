pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'truongdocker1'
        DOCKER_CREDENTIALS_ID = 'dockerhub-creds'
        IMAGE_NAME = 'bookstore-ai-service'
        TAG = "${BUILD_NUMBER}"

        K8S_DEPLOYMENT = 'ai-service-deployment'
        K8S_CONTAINER = 'ai-service'

        AI_PROVIDER_CREDENTIALS_ID = 'ai-provider'
        AI_API_KEY_CREDENTIALS_ID = 'ai-api-key'
        GEMINI_API_KEY_CREDENTIALS_ID = 'gemini-api-key'
        GROQ_API_KEY_CREDENTIALS_ID = 'groq-api-key'
        DEEPSEEK_BASE_URL_CREDENTIALS_ID = 'deepseek-base-url'
        DEEPSEEK_API_KEY_CREDENTIALS_ID = 'deepseek-api-key'
    }

    tools {
        maven 'Maven 3.9'
        jdk 'JDK 21'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    dockerImage = docker.build(
                        "${DOCKER_REGISTRY}/${IMAGE_NAME}:${TAG}",
                        "."
                    )
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    docker.withRegistry(
                        'https://index.docker.io/v1/',
                        "${DOCKER_CREDENTIALS_ID}"
                    ) {
                        dockerImage.push()
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                withCredentials([
                    string(credentialsId: "${AI_PROVIDER_CREDENTIALS_ID}", variable: 'AI_PROVIDER'),
                    string(credentialsId: "${AI_API_KEY_CREDENTIALS_ID}", variable: 'AI_API_KEY'),
                    string(credentialsId: "${GEMINI_API_KEY_CREDENTIALS_ID}", variable: 'GEMINI_API_KEY'),
                    string(credentialsId: "${GROQ_API_KEY_CREDENTIALS_ID}", variable: 'GROQ_API_KEY'),
                    string(credentialsId: "${DEEPSEEK_BASE_URL_CREDENTIALS_ID}", variable: 'DEEPSEEK_BASE_URL'),
                    string(credentialsId: "${DEEPSEEK_API_KEY_CREDENTIALS_ID}", variable: 'DEEPSEEK_API_KEY')
                ]) {
                    sh '''
                export KUBECONFIG=/var/jenkins_home/.kube/config

                # Update image tag robustly, even if the workspace still has an older build tag.
                sed -i "s|image: .*${IMAGE_NAME}:.*|image: ${DOCKER_REGISTRY}/${IMAGE_NAME}:${TAG}|g" k8s/deployment.yaml

                # ConfigMap is safe to keep in Git.
                kubectl apply -f k8s/configmap.yaml

                # App secret from Jenkins Credentials. Do not apply k8s/secret.example.yaml with real values.
                kubectl create secret generic ai-service-secret \
                  --from-literal=AI_PROVIDER="$AI_PROVIDER" \
                  --from-literal=AI_API_KEY="$AI_API_KEY" \
                  --from-literal=GEMINI_API_KEY="$GEMINI_API_KEY" \
                  --from-literal=GROQ_API_KEY="$GROQ_API_KEY" \
                  --from-literal=DEEPSEEK_BASE_URL="$DEEPSEEK_BASE_URL" \
                  --from-literal=DEEPSEEK_API_KEY="$DEEPSEEK_API_KEY" \
                  --dry-run=client -o yaml | kubectl apply -f -

                # Deploy app.
                kubectl apply -f k8s/deployment.yaml
                kubectl apply -f k8s/service.yaml

                if ! kubectl rollout status deployment/${K8S_DEPLOYMENT} --timeout=300s; then
                  echo "Rollout failed. Collecting Kubernetes diagnostics..."
                  kubectl describe deployment/${K8S_DEPLOYMENT} || true
                  kubectl get pods -l app=ai-service -o wide || true
                  kubectl describe pods -l app=ai-service || true
                  kubectl logs -l app=ai-service --all-containers=true --tail=200 || true
                  exit 1
                fi
                '''
                }
            }
        }
    }

    post {
        success {
            echo "Build & Deploy SUCCESS"
        }
        failure {
            echo "Build FAILED"
        }
    }
}
