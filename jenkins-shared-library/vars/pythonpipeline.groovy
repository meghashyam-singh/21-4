def call(configMap) {
    pipeline {
        agent {
            node {
                label 'AGENT-1'
            }
        }
        environment {
            APPVERSION = ""
            REGION = 'us-east-1'
            PROJECT = 'roboshop'
            ACCOUNT_ID = '515138251473'
            BRANCH = "${configMap.BRANCH}"
            COMPONENT = "${configMap.COMPONENT}"
            GIT_URL = "${configMap.GIT_URL}"
        }
        options {
            timeout(time:15, unit: 'MINUTES')
            disableConcurrentBuilds()
        }
        stages {
            stage('clean workspace') {
                steps {
                    cleanWs()
                }
            }
            stage('get code') {
                steps {
                    git url: "${GIT_URL}", branch: "${BRANCH}"
                }
            }
            stage('read version') {
                steps {
                    dir("${COMPONENT}") {
                        script {
                            APPVERSION = readFile('version.txt').trim()
                            echo "APPVERSION IS: ${APPVERSION}"
                        }
                    }
                }
            }
            stage('build code') {
                steps {
                    dir("${COMPONENT}") {
                        sh "pip3 install -r requirements.txt"
                    }
                }
            }
            stage('sonarqube') {
                steps {
                    dir("${COMPONENT}") {
                        script {
                            def scannerHome = tool 'sonar-8.0'
                            withSonarqubeEnv('sonar-server') {
                                sh "${scannerHome}/bin/sonar-scanner"
                            }
                        }
                    }
                }
            }
            stage('qualityGate') {
                steps {
                    script {
                        timeout(time:2, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }
            }
            stage('image-build') {
                steps {
                    sh "docker build -t ${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD-NUMBER} ./${COMPONENT}"
                }
            }
            stage('image scan') {
                steps {
                    sh "trivy image ${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD-NUMBER} > ${COMPONENT}-image-scan-report.txt"
                }
            }
            stage('image-push') {
                steps {
                    script {
                        withAWS(region:"${REGION}",credentials:'aws-creds') {
                            sh """
                            aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com
                            docker tag ${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD-NUMBER} ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD-NUMBER}
                            docker push ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD-NUMBER}
                            """
                        }
                    }
                }
            }
            stage('trigger deployment job') {
                steps {
                    build job: "${COMPONENT}-cd-pipeline",
                    propagate: "false",
                    wait: "false"
                }
            }
        }
    }
}