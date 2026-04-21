def call(configMap) {
    pipeline {
        agent {
            node {
                label 'AGENT-1'
            }
        }
        environment {
            REGION = "${configMap.REGION}"
            COMPONENT = "${configMap.COMPONENT}"
        }
        stages {
            stage('deploy') {
                steps {
                    dir("${COMPONENT}") {
                        withAWS(region:"${REGION}",credentials:'aws-creds') {
                            sh """
                            aws eks update-kubeconfig --region ${REGION} --name roboshop-cluster
                            kubectl apply -f manifestfile.yaml
                            """
                        }
                    }
                }
            }
            stage('wait for deployment') {
                steps {
                    sleep time:60, unit: 'SECONDS'
                }
            }
            stage('health check') {
                steps {
                    withAWS(region:"${REGION}",credentials:'aws-creds') {
                        sh """
                        kubectl roll status deployment ${COMPONENT} -n roboshop
                        """
                    }
                }
            }
        }
    }
}