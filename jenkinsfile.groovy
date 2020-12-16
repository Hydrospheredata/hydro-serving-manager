properties([
  parameters([
    choice(choices: ['patch','minor','major'], name: 'patchVersion', description: 'What needs to be bump?'),
    string(defaultValue:'', description: 'Force set newVersion or leave empty', name: 'newVersion', trim: false),
    string(defaultValue:'', description: 'Set grpcVersion or leave empty', name: 'grpcVersion', trim: false),
    choice(choices: ['local', 'global'], name: 'release', description: 'It\'s local release or global?'),
   ])
])

//TODO:remove if work with git
BRANCH_NAME = 'master'

SERVICENAME = 'hydro-serving-manager'
SEARCHPATH = './project/Dependencies.scala'
SEARCHGRPC = '  val servingGrpcScala = '
TESTCMD = 'sbt --batch test'
BUILDCMD = 'sbt --batch docker'
REGISTRYURL = 'harbor.hydrosphere.io/hydro-test'
SERVICEIMAGENAME = 'serving-manager'
GITHUBREPO  = "github.com/Hydrospheredata/hydro-serving-manager.git"

def checkoutRepo(String repo){
      git changelog: false, credentialsId: 'HydroRobot_AccessToken', poll: false, url: repo
}

def getVersion(){
    try{
        //remove only quotes
        version = sh(script: "git rev-parse HEAD", returnStdout: true ,label: "get version").trim()
        return version
    }catch(err){
        return "$err" 
    }
}

def bumpVersion(String currentVersion,String newVersion, String patch, String path){
    sh script: """cat <<EOF> ${WORKSPACE}/bumpversion.cfg
[bumpversion]
current_version = 0.0.0
commit = False
tag = False
parse = (?P<major>\\d+)\\.(?P<minor>\\d+)\\.(?P<patch>\\d+)
serialize =
    {major}.{minor}.{patch}

EOF""", label: "Set bumpversion configfile"
    if (newVersion != null && newVersion != ''){ //TODO: needs verify valid semver
        sh("echo $newVersion > version") 
    }else{
        sh("bumpversion $patch $path --config-file '${WORKSPACE}/bumpversion.cfg' --allow-dirty --verbose --current-version '$currentVersion'")   
    }
}

def bumpGrpc(String newVersion, String search, String patch, String path){
    sh script: "cat $path | grep '$search' > tmp", label: "Store search value in tmp file"
    currentVersion = sh(script: "cat tmp | cut -d'=' -f2 | sed 's/\"//g' | sed 's/,//g'", returnStdout: true, label: "Get current version").trim()
    sh script: "sed -i -E \"s/$currentVersion/$newVersion/\" tmp", label: "Bump temp version"
    sh script: "sed -i 's/\\\"/\\\\\"/g' tmp", label: "remove quote and space from version"
    sh script: "sed -i \"s/.*$search.*/\$(cat tmp)/g\" $path", label: "Change version"
    sh script: "rm -rf tmp", label: "Remove temp file"
}

//Команды для запуска тестов (каждой репе своя?)
def runTest(){
    sh script: "$TESTCMD", label: "Run test task"
}

def buildDocker(){
    //run build command and store build tag  
    sh script: "$BUILDCMD", label: "Run build docker task";
}

def pushDocker(String registryUrl, String dockerImage){
    //push docker image to registryUrl
    withCredentials([usernamePassword(credentialsId: 'hydro_harbor_docker_registry', passwordVariable: 'password', usernameVariable: 'username')]) {
      sh script: "docker login --username $username --password $password $registryUrl"
      sh script: "docker tag hydrosphere/$dockerImage $registryUrl/$dockerImage",label: "set tag to docker image"
      sh script: "docker push $registryUrl/$dockerImage",label: "push docker image to registry"
    }
}

def updateDockerCompose(String newVersion){
  dir('docker-compose'){
    //Change template
    sh script: "sed \"s/.*image:.*/    image: harbor.hydrosphere.io\\/hydro-test\\/serving-manager:$newVersion/g\" hydro-serving-manager.service.template > hydro-serving-manager.compose", label: "sed hydro-manager version"
    //Merge compose into 1 file
    composeMerge = "docker-compose"
    composeService = sh label: "Get all template", returnStdout: true, script: "ls *.compose"
    list = composeService.split( "\\r?\\n" )
    for(l in list){
        composeMerge = composeMerge + " -f $l"
    }
    composeMerge = composeMerge + " config > docker-compose.yaml"
    sh script: "$composeMerge", label:"Merge compose file"
    sh script: "cp docker-compose.yaml ../docker-compose.yaml"
  }
}

def updateHelmChart(String newVersion){
  dir('helm'){
    //Change template
    sh script: "sed -i \"s/.*full:.*/  full: harbor.hydrosphere.io\\/hydro-test\\/serving-manager:$newVersion/g\" manager/values.yaml", label: "sed hydro-manager version"
    sh script: "sed -i \"s/.*harbor.hydrosphere.io\\/hydro-test\\/serving-manager:.*/  full: harbor.hydrosphere.io\\/hydro-test\\/serving-manager:$newVersion/g\" dev.yaml", label: "sed hydro-manager dev stage version"

    //Refresh readme for chart
    sh script: "frigate gen manager --no-credits > manager/README.md"

    //Lint manager
    dir('manager'){
        sh script: "helm dep up", label: "Dependency update"
        sh script: "helm lint .", label: "Lint auto-od chart"
        sh script: "helm template -n serving --namespace hydrosphere . > test.yaml", label: "save template to file"
        sh script: "polaris audit --audit-path test.yaml -f yaml", label: "lint template by polaris"
        sh script: "polaris audit --audit-path test.yaml -f score", label: "get polaris score"
        sh script: "rm -rf test.yaml", label: "remove test.yaml"
    }
    //Lint serving and bump version for dev stage
    dir('serving'){
        sh script: "helm dep up", label: "Dependency update"
        sh script: "helm lint .", label: "Lint all charts"
        sh script: "helm template -n serving --namespace hydrosphere . > test.yaml", label: "save template to file"
        sh script: "polaris audit --audit-path test.yaml -f yaml", label: "lint template by polaris"
        sh script: "polaris audit --audit-path test.yaml -f score", label: "get polaris score"
        sh script: "rm -rf test.yaml", label: "remove test.yaml"
    }
  }
}

node('hydrocentral') {
    stage('SCM'){
     // git changelog: false, credentialsId: 'HydroRobot_AccessToken', poll: false, url: 'https://github.com/Hydrospheredata/hydro-serving-manager.git' 
      checkoutRepo("https://github.com/Hydrospheredata/$SERVICENAME" + '.git')
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
      if (BRANCH_NAME == 'master' || BRANCH_NAME == 'main'){
          newVersion = getVersion()
          bumpGrpc(grpcVersion, SEARCHGRPC, params.patchVersion, SEARCHPATH) 
          buildDocker()
          pushDocker(REGISTRYURL, SERVICEIMAGENAME+":$newVersion")
          //Update helm and docker-compose if release 
        if (params.release == 'local'){
          dir('release'){
            //bump only image tag
            withCredentials([usernamePassword(credentialsId: 'HydroRobot_AccessToken', passwordVariable: 'Githubpassword', usernameVariable: 'Githubusername')]) {
              git changelog: false, credentialsId: 'HydroRobot_AccessToken', url: "https://$Githubusername:$Githubpassword@github.com/Hydrospheredata/hydro-serving.git"      
              updateHelmChart("$newVersion")
              updateDockerCompose("$newVersion")
              sh script: "git commit --allow-empty -a -m 'Releasing $SERVICENAME:$newVersion'",label: "commit to git chart repo"
              sh script: "git push https://$Githubusername:$Githubpassword@github.com/Hydrospheredata/hydro-serving.git --set-upstream master",label: "push to git"
            }
          }
        }
      }
    }
}