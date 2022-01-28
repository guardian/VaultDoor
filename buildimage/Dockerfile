FROM centos:8

COPY bintray--sbt-rpm.repo /etc/yum.repos.d/bintray-sbt-rpm.repo
RUN yum -y groupinstall "Development tools" && yum -y update && yum -y install curl sbt rpm-build java-1.8.0-openjdk-devel java-1.8.0-openjdk-headless && yum clean all && rm -rf /var/cache/yum
RUN useradd build -u 1001 -g 999
USER build
RUN cd; mkdir project; echo sbt.version = 1.2.8 > project/build.properties && echo exit | sbt