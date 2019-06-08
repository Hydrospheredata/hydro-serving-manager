def repository = 'hydro-serving-manager'

def buildFunction={
    sh "sbt test"
    sh "sbt it:testOnly"
    sh "sbt docker"
}

def collectTestResults = {
    junit testResults: '**/target/test-reports/io.hydrosphere*.xml', allowEmptyResults: true
}

pipelineCommon(
        repository,
        false, //needSonarQualityGate,
        ["hydrosphere/serving-manager"],
        collectTestResults,
        buildFunction,
        buildFunction,
        buildFunction,
        null,
        "",
        "",
        {},
        commitToCD("manager")
)
