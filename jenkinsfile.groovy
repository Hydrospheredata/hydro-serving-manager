pipeline {
    agent any

  stages {  
    stage('SCM') {
      steps {
        git 'https://github.com/Hydrospheredata/hydro-serving-manager.git'
      }
    }

    stage('SonarQube analysis') {
      steps{
        script {
          def scannerHome = tool 'Sonarcloud';
          withSonarQubeEnv('Sonarcloud') { // If you have configured more than one global server connection, you can specify its name
            if (env.CHANGE_ID == null) {
              sh "${scannerHome}/bin/sonar-scanner \
                -Dsonar.projectKey=Hydrospheredata_hydro-serving-manager \
                -Dsonar.organization=hydrosphere \
                -Dsonar.sources=. \
                -Dsonar.branch.name=${env.BRANCH_NAME} \
                -Dsonar.host.url=https://sonarcloud.io \
                -Dsonar.login=2640dff4cc2bf71eb1d89bc17675b8518b71c125"
            } else {
              sh "${scannerHome}/bin/sonar-scanner \
                -Dsonar.projectKey=Hydrospheredata_hydro-serving-manager \
                -Dsonar.organization=hydrosphere \
                -Dsonar.sources=. \
                -Dsonar.pullrequest.key=${env.CHANGE_ID} \
                -Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} \
                -Dsonar.pullrequest.base=${env.CHANGE_TARGET} \
                -Dsonar.host.url=https://sonarcloud.io \
                -Dsonar.login=2640dff4cc2bf71eb1d89bc17675b8518b71c125"
            }
          }
        }
      }
    }

//  stage("Quality Gate"){
//    timeout(time: 1, unit: 'HOURS') { // Just in case something goes wrong, pipeline will be killed after a timeout
//      def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
//      if (qg.status != 'OK') {
//        error "Pipeline aborted due to quality gate failure: ${qg.status}"
//      }
//    }
//  }

    stage("trigger-central") {
        steps{    
          build job: 'provectus.com/hydro-central/master', parameters: [
            [$class: 'StringParameterValue',
            name: 'PROJECT',
            value: 'manager'
            ],
            [$class: 'StringParameterValue',
            name: 'BRANCH',
            value: env.BRANCH_NAME
            ]
          ]
        }
    }
  }
}
