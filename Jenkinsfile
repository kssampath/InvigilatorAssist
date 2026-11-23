// InvigilatorAssist CI/CD: Jenkins → Azure Container Registry → GitOps commit → ArgoCD → AKS
//
//   Every commit on master (SCM polling every 2 minutes; a GitHub webhook also works):
//     1. Backend: Maven unit tests (+ Testcontainers integration tests when RUN_INTEGRATION_TESTS)
//     2. Frontend: lint, unit tests, production build
//     3. Docker: build the API and web images, tagged <build>-<commit>
//     4. Push both images to ACR
//     5. GitOps: write the new tag into k8s/kustomization.yaml and push it ([skip ci] commit)
//     6. ArgoCD notices the commit and syncs AKS (pull-based; Jenkins never touches the cluster)
//     7. Verify: wait until <APP_URL>/api/health reports the new version (when APP_URL is set)
//
// Agent requirements, all provided by ci/jenkins (Jenkins configured as code):
//   PATH: git, mvn (JDK 21), node 22 + npm, docker CLI with buildx, connected to a Docker daemon
//   env:  ACR_LOGIN_SERVER   e.g. acrinvig1a2b3c.azurecr.io                  (required)
//         APP_URL            e.g. http://20.40.60.80, the web LoadBalancer   (optional, enables step 7)
//   credentials: acr-credentials     username/password for ACR
//                github-credentials  GitHub username / personal access token (repo scope) for the GitOps push
//
// The previous Azure Container Apps + Terraform pipeline is kept as Jenkinsfile.container-apps.

pipeline {
  agent any

  options {
    skipDefaultCheckout()
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 45, unit: 'MINUTES')
  }

  triggers {
    pollSCM('H/2 * * * *')
  }

  parameters {
    booleanParam(name: 'RUN_INTEGRATION_TESTS', defaultValue: false,
                 description: 'Also run the Testcontainers integration tests (needs Docker networking from the agent)')
    booleanParam(name: 'UPDATE_GITOPS', defaultValue: true,
                 description: 'Commit the new image tag to k8s/kustomization.yaml so ArgoCD deploys it')
  }

  environment {
    GITOPS_BRANCH   = 'master'
    API_IMAGE       = "${env.ACR_LOGIN_SERVER}/invigilator-api"
    WEB_IMAGE       = "${env.ACR_LOGIN_SERVER}/invigilator-web"
    DOCKER_BUILDKIT = '1'
    DOCKER_PLATFORM = 'linux/amd64'   // AKS nodes are amd64, even when Jenkins runs on an Apple Silicon Mac
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script {
          env.GIT_SHORT = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
          env.IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_SHORT}"
          String subject = sh(script: 'git log -1 --pretty=%s', returnStdout: true).trim()
          // Jenkins' own GitOps commit: nothing to build, and building it would loop.
          env.SKIP_CI = subject.contains('[skip ci]') ? 'true' : 'false'
          if (env.SKIP_CI == 'true') {
            currentBuild.description = "GitOps commit ${env.GIT_SHORT}: skipped"
            currentBuild.result = 'NOT_BUILT'
          } else {
            if (!env.ACR_LOGIN_SERVER?.trim()) {
              error 'ACR_LOGIN_SERVER is not set. Add it to ci/jenkins/.env (or Manage Jenkins → System → Environment variables).'
            }
            String shortSubject = subject.length() > 60 ? subject.substring(0, 60) : subject
            currentBuild.description = "${env.IMAGE_TAG}: ${shortSubject}"
            echo "Building ${env.GIT_SHORT} as image tag ${env.IMAGE_TAG}"
          }
        }
      }
    }

    stage('Build & test') {
      when { environment name: 'SKIP_CI', value: 'false' }
      parallel {
        stage('Backend (Maven)') {
          steps {
            dir('backend') {
              sh "mvn -B -ntp ${params.RUN_INTEGRATION_TESTS ? 'verify' : 'test'}"
            }
          }
          post {
            always {
              junit allowEmptyResults: true,
                    testResults: 'backend/target/surefire-reports/*.xml, backend/target/failsafe-reports/*.xml'
            }
          }
        }
        stage('Frontend (npm)') {
          steps {
            dir('frontend') {
              sh 'npm ci --no-audit --no-fund'
              sh 'npm run lint'
              sh 'npx vitest run --reporter=default --reporter=junit --outputFile=test-results/junit.xml'
              sh 'npm run build'
            }
          }
          post {
            always { junit allowEmptyResults: true, testResults: 'frontend/test-results/*.xml' }
          }
        }
      }
    }

    stage('Docker build') {
      when { environment name: 'SKIP_CI', value: 'false' }
      steps {
        sh '''
          docker build --pull --platform "$DOCKER_PLATFORM" --build-arg APP_VERSION="$IMAGE_TAG" \
            -t "$API_IMAGE:$IMAGE_TAG" -t "$API_IMAGE:latest" backend
          docker build --pull --platform "$DOCKER_PLATFORM" --build-arg APP_VERSION="$IMAGE_TAG" \
            -t "$WEB_IMAGE:$IMAGE_TAG" -t "$WEB_IMAGE:latest" frontend
          docker image ls --format 'table {{.Repository}}\t{{.Tag}}\t{{.Size}}' | grep -E "invigilator-(api|web)" | grep "$IMAGE_TAG"
        '''
      }
    }

    stage('Push to ACR') {
      when { environment name: 'SKIP_CI', value: 'false' }
      steps {
        withCredentials([usernamePassword(credentialsId: 'acr-credentials',
                                          usernameVariable: 'ACR_USER', passwordVariable: 'ACR_PASS')]) {
          sh '''
            echo "$ACR_PASS" | docker login "$ACR_LOGIN_SERVER" --username "$ACR_USER" --password-stdin
            for image in "$API_IMAGE" "$WEB_IMAGE"; do
              docker push "$image:$IMAGE_TAG"
              docker push "$image:latest"
            done
          '''
        }
      }
      post {
        always { sh 'docker logout "$ACR_LOGIN_SERVER" >/dev/null 2>&1 || true' }
      }
    }

    stage('Update GitOps manifests') {
      when {
        allOf {
          environment name: 'SKIP_CI', value: 'false'
          expression { params.UPDATE_GITOPS }
        }
      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-credentials',
                                          usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
          sh 'ci/scripts/gitops-bump.sh'
        }
      }
    }

    stage('Verify deployment') {
      when {
        allOf {
          environment name: 'SKIP_CI', value: 'false'
          expression { return params.UPDATE_GITOPS && (env.APP_URL ?: '').trim().length() > 0 }
        }
      }
      steps {
        // ArgoCD polls Git every ~3 minutes, then the rolling update needs a minute or two.
        sh 'ci/scripts/wait-for-version.sh "$APP_URL" "$IMAGE_TAG" 600'
      }
    }
  }

  post {
    success {
      script {
        if (env.SKIP_CI == 'false') {
          echo "Pushed ${env.API_IMAGE}:${env.IMAGE_TAG} and ${env.WEB_IMAGE}:${env.IMAGE_TAG}"
        }
      }
    }
    always {
      // Drop dangling layers from earlier builds so the agent's disk does not fill up.
      sh 'docker image prune -f >/dev/null 2>&1 || true'
    }
  }
}
