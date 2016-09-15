// Pass your Github username as a parameter to this seed job, e.g.
// GITHUB_USERNAME="John-Doe"
// Pass a docker image prefix as a parameter as well.
// If you have an account on docker hub, your hub username is ideal.
// DOCKER_USERNAME="johndoe"

projectName = "go-roman"
repositoryUrl = "https://github.com/${GITHUB_USERNAME}/go-roman.git"

deliverJobName = "0.deliver-${projectName}_GEN"
buildJobName = "1.build-${projectName}_GEN"
testJobName = "2.test-${projectName}_GEN"
releaseJobName = "3.release-${projectName}_GEN"
viewName = "${projectName}-jobs_GEN"
pipelineName = "${projectName}-pipeline_GEN"


job(buildJobName) {
    logRotator(-1, 5, -1, -1)
    Utils.configureGit(it, "${repositoryUrl}", 'master')
    envVars = [GITHUB_USERNAME: "${GITHUB_USERNAME}",
            DOCKER_USERNAME: "${DOCKER_USERNAME}"]
    Utils.configureEnvVars(it, envVars)
    steps {
        shell('./build.sh')
    }
    publishers {
        downstreamParameterized {
            trigger(testJobName) {
                condition('SUCCESS')
                parameters {
                    predefinedProp('GITHUB_USERNAME', '${GITHUB_USERNAME}')
                    predefinedProp('DOCKER_USERNAME', '${DOCKER_USERNAME}')
                    gitRevision(false)
                    propertiesFile('props.env', failTriggerOnMissing = true)
                }
            }
        }
    }
}

job(testJobName) {
    Utils.configureGit(it, "${repositoryUrl}", 'master')
    logRotator(-1, 40, -1, -1)
    parameters {
        stringParam('GITHUB_USERNAME', '', 'GITHUB_USERNAME')
        stringParam('DOCKER_USERNAME', '', 'DOCKER_USERNAME')
        stringParam('VERSION', '', 'version of the application')
        stringParam('IMAGEID', '', 'The docker image to test')
        stringParam('cid', '', 'The container ID')
    }
    steps {
        shell('./test.sh')
        environmentVariables {
            propertiesFile('props.env')
        }
    }
    publishers {
        downstreamParameterized {
            trigger(releaseJobName) {
                condition('SUCCESS')
                parameters {
                    predefinedProp('VERSION', '${VERSION}')
                    predefinedProp('GITHUB_USERNAME', '${GITHUB_USERNAME}')
                    predefinedProp('DOCKER_USERNAME', '${DOCKER_USERNAME}')
                }
            }
        }
    }
}

job(releaseJobName) {
    Utils.configureGit(it, "${repositoryUrl}", 'master')
    logRotator(-1, 5, -1, -1)
    parameters {
        stringParam('GITHUB_USERNAME', '', 'GITHUB_USERNAME')
        stringParam('DOCKER_USERNAME', '', 'DOCKER_USERNAME')
        stringParam('VERSION', '', 'version of the application')
    }
    steps {
        shell('./deploy.sh')
    }
}

listView(viewName) {
    description("All ${projectName} project related jobs")
    jobs {
        regex(".*-${projectName}.*")
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

buildPipelineView(pipelineName) {
    title("Project ${projectName} CI Pipeline")
    displayedBuilds(50)
    selectedJob("${buildJobName}")
    alwaysAllowManualTrigger()
    showPipelineParametersInHeaders()
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(60)
}
