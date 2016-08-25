// Pass your Github username as a parameter to this seed job, e.g.
// GITHUB_USERNAME="John-Doe"
// Pass a docker image prefix as a parameter as well. 
// If you have an account on docker hub, your hub username is ideal.
// DOCKER_USERNAME="johndoe"

projectName = "webserver"
repositoryUrl = "https://github.com/${GITHUB_USERNAME}/gowebserver.git"

buildJobName = "1.build-${projectName}_GEN"
testJobName = "2.test-${projectName}_GEN"
releaseJobName = "3.release-${projectName}_GEN"
viewName = "${projectName}-jobs_GEN"
pipelineName = "${projectName}-pipeline_GEN"

job(buildJobName) {
    logRotator(-1, 5, -1, -1)
    Utils.configureGit(it, "${repositoryUrl}")
    envVars = [GITHUB_USERNAME: "${GITHUB_USERNAME}", 
            DOCKER_USERNAME: "${DOCKER_USERNAME}"]
    Utils.configureEnvVars(it, envVars)
    steps {
        shell('''\
            echo "version=\$(cat version.txt)" > props.env
            sudo docker build --no-cache -t ${DOCKER_USERNAME}/http-app:snapshot .
            imageid=$(sudo docker images | grep ${DOCKER_USERNAME}/http-app | grep snapshot | awk '{print $3}')
            cid=$(sudo docker ps --filter="name=testing-app" -q -a)
            if [ ! -z "$cid" ]
            then
                sudo docker rm -f testing-app
            fi

            cid=$(sudo docker run -d --name testing-app -p 8001:8000 ${DOCKER_USERNAME}/http-app:snapshot)
            echo "cid=$cid" >> props.env
            echo "IMAGEID=$imageid" >> props.env
            cat props.env
            cip=$(sudo docker inspect --format '{{ .NetworkSettings.IPAddress }}' ${cid})
            sudo docker run --rm rufus/siege-engine -g http://$cip:8000/
            [ $? -ne 0 ] && exit 1
            sudo docker kill ${cid}
            sudo docker rm ${cid}'''.stripIndent())
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
    logRotator(-1, 40, -1, -1)
    parameters {
        stringParam('GITHUB_USERNAME', '', 'GITHUB_USERNAME')
        stringParam('DOCKER_USERNAME', '', 'DOCKER_USERNAME')
        stringParam('version', '', 'version of the application')
        stringParam('IMAGEID', '', 'The docker image to test')
        stringParam('cid', '', 'The container ID')
    }
    steps {
        shell('''\
                cid=$(sudo docker ps --filter="name=testing-app" -q -a)
                if [ ! -z "$cid" ]
                then
                    sudo docker rm -f testing-app
                fi
                testing_cid=$(sudo docker run -d --name testing-app -p 8000:8000  $IMAGEID)
                echo "testing_cid=$testing_cid" > props.env'''.stripIndent())
        environmentVariables {
            propertiesFile('props.env')
        }
        shell('''\
                cip=$(sudo docker inspect --format '{{ .NetworkSettings.IPAddress }}' ${testing_cid})
                sudo docker run --rm rufus/siege-engine  -b -t60S http://$cip:8000/ > output 2>&1'''.stripIndent())
        shell('''\
                avail=$(cat output | grep Availability | awk '{print $2}')
                echo $avail
                # shell uses = to compare strings, bash ==
                if [ "$avail" = "100.00" ]
                then
	                echo "Availability high enough"
	                sudo docker tag -f $IMAGEID ${DOCKER_USERNAME}/http-app:stable
	                exit 0
                else
	                echo "Availability too low"
	                exit 1
                fi'''.stripIndent())

    }
    publishers {
        downstreamParameterized {
            trigger(releaseJobName) {
                condition('SUCCESS')
                parameters {
                    predefinedProp('VERSION', '${version}')
                    predefinedProp('GITHUB_USERNAME', '${GITHUB_USERNAME}')
                    predefinedProp('DOCKER_USERNAME', '${DOCKER_USERNAME}')
                }
            }
        }
    }
}

job(releaseJobName) {
    logRotator(-1, 5, -1, -1)
    parameters {
        stringParam('GITHUB_USERNAME', '', 'GITHUB_USERNAME')
        stringParam('DOCKER_USERNAME', '', 'DOCKER_USERNAME')
        stringParam('VERSION', '', 'version of the application')
    }
    steps {
        shell('''\
                sudo docker tag -f ${DOCKER_USERNAME}/http-app:stable ${DOCKER_USERNAME}/http-app:latest
                sudo docker tag -f ${DOCKER_USERNAME}/http-app:stable ${DOCKER_USERNAME}/http-app:$VERSION
                # no git here yet
                # sudo docker tag http-app/http-app:$(git describe)
                cid=$(sudo docker ps --filter="name=deploy-app" -q -a)
                if [ ! -z "$cid" ]
                then
                    sudo docker rm -f deploy-app
                fi
                sudo docker run -d --name deploy-app -p 8080:8000 ${DOCKER_USERNAME}/http-app:latest'''.stripIndent())
        shell('''\
                sudo docker ps |grep ${DOCKER_USERNAME}/http-app
                sudo docker images |grep ${DOCKER_USERNAME}/http-app'''.stripIndent())
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
