# Jenkins controller for InvigilatorAssist, ready to run the Jenkinsfile with no manual tool setup:
#   JDK 21 + Maven 3.9 (backend), Node 22 (frontend), Docker CLI + buildx (images; uses the host's daemon).
# Plugins, credentials and the pipeline job are created from casc.yaml at start-up (Configuration as Code).
FROM jenkins/jenkins:lts-jdk21

USER root
ARG MAVEN_VERSION=3.9.11
COPY --from=eclipse-temurin:21-jdk /opt/java/openjdk /opt/jdk21

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl gnupg jq \
 && install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
      > /etc/apt/sources.list.d/docker.list \
 && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin nodejs \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      | tar -xz -C /opt \
 && printf '#!/bin/sh\nJAVA_HOME=/opt/jdk21 exec /opt/apache-maven-%s/bin/mvn "$@"\n' "${MAVEN_VERSION}" > /usr/local/bin/mvn \
 && chmod +x /usr/local/bin/mvn

USER jenkins
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Xmx1g" \
    CASC_JENKINS_CONFIG=/var/jenkins_casc/casc.yaml
