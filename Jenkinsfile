pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'truongdocker1'
        DOCKER_CREDENTIALS_ID = 'dockerhub-creds'

        IMAGE_NAME = 'bookstore-ai-service'

        // version nên linh hoạt theo build number (chuẩn CI/CD)
        TAG = "${BUILD_NUMBER}"
        
        K8S_DEPLOYMENT = 'ai-service-deployment'
        K8S_CONTAINER = 'ai-service'
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
                sh """
                export KUBECONFIG=/var/jenkins_home/.kube/config

                # Cập nhật tag động vào file deployment.yaml
                sed -i "s|image: truongdocker1/bookstore-ai-service:latest|image: ${DOCKER_REGISTRY}/${IMAGE_NAME}:${TAG}|g" k8s/deployment.yaml

                # Apply cấu hình K8s
                kubectl apply -f k8s/deployment.yaml
                kubectl apply -f k8s/service.yaml

                # Đợi quá trình deploy hoàn tất
                kubectl rollout status deployment/${K8S_DEPLOYMENT}
                """
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