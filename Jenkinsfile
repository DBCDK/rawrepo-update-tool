#!groovy
//Blah
def workerNode = "devel11"

pipeline {
    agent { label workerNode }
    tools {
        jdk 'jdk8'
        maven 'Maven 3'
    }
    environment {
        GITLAB_PRIVATE_TOKEN = credentials("metascrum-gitlab-api-token")
    }
    stages {
        stage("clear workspace") {
            steps {
                deleteDir()
                checkout scm
            }
        }
        stage("install") {
            steps {
                sh "mvn -B clean install"
            }
        }


        stage("deploy to maven repository") {
            when {
                branch "master"
            }
            steps {
                sh "mvn deploy -Dmaven.test.skip=true"
            }
        }
    }
}
