properties([
  parameters([
    choice(choices: ['patch','minor','major'], name: 'patchVersion', description: 'What needs to be bump?'),
    string(defaultValue:'', description: 'Force set newVersion or leave empty', name: 'newVersion', trim: false),
    string(defaultValue:'', description: 'Set grpcVersion or leave empty', name: 'grpcVersion', trim: false),
    choice(choices: ['local', 'global'], name: 'releaseType', description: 'It\'s local release or global?'),
   ])
])
 
SERVICENAME = 'hydro-serving-manager'
SEARCHPATH = './project/Dependencies.scala'
SEARCHGRPC = 'val servingGrpcScala'
REGISTRYURL = 'hydrosphere'
SERVICEIMAGENAME = 'serving-manager'
GITHUBREPO  = "github.com/Hydrospheredata/hydro-serving-manager.git"
CHARTNAME = 'manager'

def getVersion(){
    try{
      if (params.releaseType == 'global'){
        //remove only quotes
        version = sh(script: "cat \"version\" | sed 's/\\\"/\\\\\"/g'", returnStdout: true ,label: "get version").trim()
      } else {
        //Set version as commit SHA
        version = sh(script: "git rev-parse HEAD", returnStdout: true ,label: "get version").trim()
      }
        return version
    }catch(err){
        return "$err" 
    }
}
 
//Run test command
def runTest(){
    sh script: "sbt --batch test", label: "Run test task"
}

def buildDocker(){
    //run build command and store build tag
    tagVersion = getVersion()
    sh script: "sbt --batch -DappVersion=$tagVersion docker", label: "Run build docker task";
    sh script: "sbt --batch -DappVersion=latest docker", label: "Run build docker task";
}


def pushDocker(String registryUrl, String dockerImage){
    //push docker image to registryUrl
    withCredentials([usernamePassword(credentialsId: 'hydrorobot_docker_creds', passwordVariable: 'password', usernameVariable: 'username')]) {
      sh script: "docker login --username ${username} --password ${password}"
      sh script: "docker push $registryUrl/$dockerImage",label: "push docker image to registry"
    }
}

node('hydrocentral') {
    try {
        stage('SCM'){
            autoCheckout(SERVICENAME)
            AUTHOR = sh(script:"git log -1 --pretty=format:'%an'", returnStdout: true, label: "get last commit author").trim()
            if (params.grpcVersion == ''){
                //Set grpcVersion
                grpcVersion = sh(script: "curl -Ls https://pypi.org/pypi/hydro-serving-grpc/json | jq -r .info.version", returnStdout: true, label: "get grpc version").trim()
            }
        }

        stage('Test'){
            if (env.CHANGE_ID != null){
                runTest()
            }
        }

        stage('Release'){
            if (BRANCH_NAME == 'master' || BRANCH_NAME == 'main'){ //Run only from master{
                if (params.releaseType == 'global'){
                    oldVersion = getVersion()
                    bumpVersion(getVersion(),params.newVersion,params.patchVersion,'version')
                    newVersion = getVersion()
                    bumpGrpc(grpcVersion, SEARCHGRPC, params.patchVersion, SEARCHPATH)
                } else {
                    newVersion = getVersion()
                }
 
                buildDocker()
                pushDocker(REGISTRYURL, SERVICEIMAGENAME+":$newVersion")
                //Update latest tag
                pushDocker(REGISTRYURL, SERVICEIMAGENAME+":latest")
                //Update helm and docker-compose if release 
                if (params.releaseType == 'global'){
                    releaseService(oldVersion, newVersion, SERVICEIMAGENAME)
                } else {
                    dir('release'){
                        //bump only image tag
                        autoCheckout('hydro-serving')    
                        updateHelmChart("$newVersion", SERVICEIMAGENAME, CHARTNAME )
                        updateDockerCompose("$newVersion", SERVICEIMAGENAME, SERVICENAME )
                        sh script: "git commit --allow-empty -a -m 'Releasing $SERVICEIMAGENAME:$newVersion'",label: "commit to git chart repo"
                        sh script: "git push https://$Githubusername:$Githubpassword@github.com/Hydrospheredata/hydro-serving.git --set-upstream master",label: "push to git"
                    }
                }
            }
        }
    //post if success
    if (params.releaseType == 'local' && BRANCH_NAME == 'master'){
        slackMessage()
    }
  } catch (e) {
  //post if failure
    currentBuild.result = 'FAILURE'
    if (params.releaseType == 'local' && BRANCH_NAME == 'master'){
        slackMessage()
    }
      throw e
  }
}