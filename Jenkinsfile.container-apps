// InvigilatorAssist CI/CD
//
//   every branch / PR : test backend + frontend, check Terraform formatting and validity
//   main              : build image in ACR → Terraform dev → deploy web dev → smoke test
//                       → manual approval → Terraform prod → deploy web prod → smoke test
//
// Agent requirements: label "linux", Docker (for Testcontainers), Azure CLI, Terraform >= 1.9,
// JDK 21 + Maven 3.9 and Node.js 22 configured as Jenkins tools named below.
//
// Jenkins credentials (Manage Jenkins → Credentials):
//   azure-sp            Username/password: service principal appId / client secret
//   azure-tenant-id     Secret text
//   azure-subscription  Secret text
//   tf-backend-account  Secret text: state storage account name printed by infra/bootstrap

def SHA = ''

pipeline {
  agent { label 'linux' }

  tools {
    jdk 'jdk21'
    maven 'maven3'
    nodejs 'node22'
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '30'))
    timeout(time: 60, unit: 'MINUTES')
  }

  environment {
    SHARED_RG = 'rg-invig-shared'
    TF_IN_AUTOMATION = 'true'
    TF_INPUT = '0'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script { SHA = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim() }
      }
    }

    stage('Test') {
      parallel {
        stage('Backend') {
          steps {
            dir('backend') {
              // verify = unit tests + Testcontainers integration tests against real PostgreSQL
              sh 'mvn -B -ntp verify'
            }
          }
          post {
            always {
              junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml, backend/target/failsafe-reports/*.xml'
            }
          }
        }
        stage('Frontend') {
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
        stage('Terraform checks') {
          steps {
            dir('infra') {
              sh 'terraform fmt -check -recursive'
              sh '''
                for env in envs/dev envs/prod; do
                  terraform -chdir=$env init -backend=false -input=false >/dev/null
                  terraform -chdir=$env validate
                done
              '''
              // Optional IaC security scan when checkov is installed on the agent.
              sh 'if command -v checkov >/dev/null; then checkov -d . --quiet --compact --soft-fail; fi'
            }
          }
        }
      }
    }

    stage('Build image') {
      when { branch 'main' }
      steps {
        script {
          withAzure {
            env.ACR_NAME = sh(script: "az acr list -g ${env.SHARED_RG} --query '[0].name' -o tsv", returnStdout: true).trim()
            // Built inside ACR: no Docker daemon or registry password needed on the agent.
            sh "az acr build --registry \"\$ACR_NAME\" --image invigilator-api:${SHA} --image invigilator-api:main backend"
          }
        }
      }
    }

    stage('Deploy dev') {
      when { branch 'main' }
      steps {
        script { deployEnvironment('dev', SHA) }
      }
    }

    stage('Approve prod') {
      when { branch 'main' }
      steps {
        timeout(time: 2, unit: 'DAYS') {
          input message: "Deploy ${SHA} to production?", ok: 'Deploy'
        }
      }
    }

    stage('Deploy prod') {
      when { branch 'main' }
      steps {
        script { deployEnvironment('prod', SHA) }
      }
    }
  }

  post {
    always {
      cleanWs(deleteDirs: true, notFailBuild: true, patterns: [[pattern: '**/backend.hcl', type: 'INCLUDE'], [pattern: '**/*.tfplan', type: 'INCLUDE']])
    }
  }
}

// ---- helpers ------------------------------------------------------------------------------------------

/** Runs the body logged in to Azure, with ARM_* variables set for Terraform. */
def withAzure(Closure body) {
  withCredentials([
    usernamePassword(credentialsId: 'azure-sp', usernameVariable: 'ARM_CLIENT_ID', passwordVariable: 'ARM_CLIENT_SECRET'),
    string(credentialsId: 'azure-tenant-id', variable: 'ARM_TENANT_ID'),
    string(credentialsId: 'azure-subscription', variable: 'ARM_SUBSCRIPTION_ID'),
    string(credentialsId: 'tf-backend-account', variable: 'TF_BACKEND_ACCOUNT'),
  ]) {
    sh '''
      az login --service-principal -u "$ARM_CLIENT_ID" -p "$ARM_CLIENT_SECRET" --tenant "$ARM_TENANT_ID" --output none
      az account set --subscription "$ARM_SUBSCRIPTION_ID"
    '''
    try {
      body()
    } finally {
      sh 'az logout || true'
    }
  }
}

/** Terraform apply for one environment, then publish the web app pointed at that environment's API. */
def deployEnvironment(String envName, String sha) {
  withAzure {
    dir("infra/envs/${envName}") {
      writeFile file: 'backend.hcl', text: """
resource_group_name  = "${env.SHARED_RG}"
storage_account_name = "${env.TF_BACKEND_ACCOUNT}"
container_name       = "tfstate"
use_azuread_auth     = true
"""
      sh 'terraform init -input=false -backend-config=backend.hcl'
      sh "terraform plan -input=false -var acr_name=\"\$ACR_NAME\" -var image_tag=${sha} -out=${envName}.tfplan"
      sh "terraform apply -input=false ${envName}.tfplan"
      env.API_URL = sh(script: 'terraform output -raw api_url', returnStdout: true).trim()
      env.WEB_URL = sh(script: 'terraform output -raw web_url', returnStdout: true).trim()
    }

    dir('frontend') {
      sh 'VITE_API_BASE_URL="$API_URL" npm run build'
      // The deployment token is read and used inside one shell step so it never lands in the build environment.
      sh """
        set +x
        SWA_TOKEN=\$(terraform -chdir=../infra/envs/${envName} output -raw web_deployment_token)
        npx --yes @azure/static-web-apps-cli@2 deploy ./dist --deployment-token "\$SWA_TOKEN" --env production
      """
    }

    // Smoke test: the API must report ready (DB reachable, Flyway done) and the web app must load.
    sh '''
      for i in $(seq 1 30); do
        if curl -fsS "$API_URL/actuator/health/readiness" >/dev/null; then echo "API ready"; break; fi
        if [ "$i" = 30 ]; then echo "API did not become ready"; exit 1; fi
        sleep 10
      done
      curl -fsS -o /dev/null "$WEB_URL"
    '''
    echo "${envName}: web ${env.WEB_URL} · api ${env.API_URL}"
  }
}
