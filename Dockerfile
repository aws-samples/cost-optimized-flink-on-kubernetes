FROM maven:3.8.1-jdk-8-slim AS builder
COPY src/ /home/app/src
COPY pom.xml /home/app/
RUN ls -l /home/app
RUN mvn -f /home/app/pom.xml clean package -Dflink.version=1.13.0

FROM flink:1.13.0
RUN mkdir -p $FLINK_HOME/usrlib
COPY --from=builder /home/app/target/aws-kinesis-analytics-java-apps-1.0.jar $FLINK_HOME/usrlib/aws-kinesis-analytics-java-apps-1.0.jar
RUN mkdir $FLINK_HOME/plugins/s3-fs-hadoop
COPY flink-s3-fs-hadoop-1.13.0.jar $FLINK_HOME/plugins/s3-fs-hadoop/

