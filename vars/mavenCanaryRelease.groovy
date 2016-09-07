#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def fabric8MavenPluginVersion = flow.getReleaseVersion "io/fabric8/fabric8-maven-plugin"

    sh "git checkout -b ${env.JOB_NAME}-${config.version}"
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -U -DnewVersion=${config.version}"
    sh "mvn clean"

    Model m = readMavenPom file: 'pom.xml'
    def groupId = m.groupId.split( '\\.' )
    def user = groupId[groupId.size()-1].trim()
    def artifactId = m.artifactId

    sh "mvn package -U io.fabric8:fabric8-maven-plugin:${fabric8MavenPluginVersion}:build"

    if (flow.isSingleNode()){
        echo 'Running on a single node, skipping docker push as not needed'

    } else {
      sh "mvn io.fabric8:fabric8-maven-plugin:${fabric8MavenPluginVersion}:push -Ddocker.push.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}"
    }

    if (flow.hasService("content-repository")) {
      try {
        sh 'mvn site site:deploy'
      } catch (err) {
        // lets carry on as maven site isn't critical
        echo 'unable to generate maven site'
      }
    } else {
      echo 'no content-repository service so not deploying the maven site report'
    }
  }