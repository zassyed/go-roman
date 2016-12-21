static def configureGit(def job, def repositoryUrl, def branchDef) {
    job.with {
        scm {
            git {
                remote {
                    name('origin')
                    url(repositoryUrl)
                    credentials('GithubCredentials')
                }
                branch(branchDef)
            }
        }
        triggers {
            scm('* * * * *')
        }
    }
}

static def configureEnv(def job, def username) {
    job.with {
        properties {
            environmentVariables {
                keepSystemVariables(true)
                keepBuildVariables(true)
                env('GITHUB_USERNAME', username)
            }
        }
    }
}

static def configureEnvVars(def job, def map) {
    job.with {
        properties {
            environmentVariables {
                keepSystemVariables(true)
                keepBuildVariables(true)
                envs(map)
            }
        }
    }
}
