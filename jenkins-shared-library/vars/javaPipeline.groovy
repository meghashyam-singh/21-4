def call(configMap) {
    pipeline {
        agent {
            node {
                label 'AGENT-1'
            }
        }
        environment {
            REGION = 'us-east-1'
            ACCOUNT_ID = '515138251473'
            APPVERSION = ''
            PROJECT = 'roboshop'
            COMPONENT = "${configMap.COMPONENT}"
            GIT_URL = "${configMap.GIT_URL}"
            BRANCH = "${configMap.BRANCH}"
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
                            def pom = readMavenPom file: 'pom.xml'
                            APPVERSION = pom.version
                            echo "APPVERSION IS: ${APPVERSION}" 
                        }
                    }
                }
            }
            stage('build code') {
                steps {
                    dir("${COMPONENT}") {
                        sh "mvn clean package"
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
            // stage('qualityGates') {
            //     steps {
            //         script {
            //             timeout(time:2, unit: 'MINUTES') {
            //                 waitForQualityGate abortPipeline: true
            //             }
            //         }
            //     }
            // }
            stage('image build') {
                steps {
                    sh "docker build -t ${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD_NUMBER} ./${COMPONENT}"
                }
            }
            // stage('image scan') {
            //     steps {
            //         sh "trivy image ${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD_NUMBER} > ${COMPONENT}-image-scam-report.txt"
            //     }
            // }
            stage('image-push') {
                steps {
                    script {
                        withAWS(region:"${REGION}",credentials:'aws-creds') {
                            sh """
                            aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com
                            docker tag ${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD_NUMBER} ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD_NUMBER}
                            docker push ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}/${COMPONENT}:${APPVERSION}-${BUILD_NUMBER}
                            """
                        }
                    }
                }
            }
        }
    }
}