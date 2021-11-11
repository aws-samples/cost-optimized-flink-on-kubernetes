## Running Cost Optimized Flink workloads on Kubernetes using EC2 Spot Instances and Amazon Elastic Kubernetes Service

This GitHub repository contains sample configuration files for running Apache Flink on Kubernetes using Amazon Elastic Kubernetes Service (Amazon EKS) and Amazon EC2 Spot Instances. We recommend reading this blog post for more information on this topic. The blog also contains the detailed tutorial for the step-by-step instructions below. 

**What you’ll run**

The Flink streaming application will read dummy Stock ticker prices send to a Amazon Kinesis Data Stream (https://aws.amazon.com/kinesis/data-streams/) by Amazon Kinesis Data Generator (https://awslabs.github.io/amazon-kinesis-data-generator/web/help.html). Then it would try to determine the highest Stock price within a per-defined window. The output will be written into files in Amazon S3.

## Step-by-step Instructions

* Create a S3 Bucket and note the name of the bucket

* Create Amazon S3 Access Policy
    ```
    aws iam create-policy --policy-name spark-s3-policy --policy-document file://flink-demo-policy.json
    ```
    Replace the <<S3_Bucket>> with the bucket name

    Note the Policy ARN

* Create an EKS cluster using the following command [Note: the default region is us-west-2]
    ```
    eksctl create cluster --name=flink-demo --node-private-networking  --without-nodegroup --asg-access --region=<<AWS Region>>
    ```
* Create the nodegroup using the [managedNodeGroups.yml](./managedNodeGroups.yml) file. Replace the <<Policy ARN>> string using the ARN string from the previous step. 
    ```
    eksctl create nodegroup -f managedNodeGroups.yml
    ```
* Create a service account
    ```
    kubectl create serviceaccount flink-service-account
    kubectl create clusterrolebinding flink-role-binding-flink --clusterrole=edit --serviceaccount=default:flink-service-account
    ```
* Download and install the Cluster Autoscaler
    ```
    curl -LO https://raw.githubusercontent.com/kubernetes/autoscaler/master/cluster-autoscaler/cloudprovider/aws/examples/cluster-autoscaler-autodiscover.yaml
    ```
    Edit it to add the cluster-name flink-demo

    Install the Cluster Autoscaler
    ```
    kubectl apply -f cluster-autoscaler-autodiscover.yaml
    ```

* Build the docker image using the docker file and push it to ECR.
    
    Create a folder for docker build.

    ```
    cd <<Folder>>
    ```
    
    Download the [Dockerfile](./Dockerfile)

    Download the code folder [src](amazon-kinesis-data-analytics-java-examples/S3Sink/src) and the [pom.xml](./pom.xml) 
    - The sample is from [aws-samples/amazon-kinesis-data-analytics-java-examples](https://github.com/aws-samples/amazon-kinesis-data-analytics-java-examples). The code has been modified to enable checkpointing
    
    Download the flink-s3-fs-hadoop jar (version 1.13) from [here](https://mvnrepository.com/artifact/org.apache.flink/flink-s3-fs-hadoop) 

    Build the image and push to ECR

    ```
    docker build --tag flink-demo .
    docker tag flink-demo:latest <<Account ID>>.dkr.ecr.<<AWS Region>>.amazonaws.com/flink-demo:latest
    aws ecr get-login-password --region <<AWS Region>> | docker login --username AWS --password-stdin <<Account ID>>.dkr.ecr.<<AWS Region>>.amazonaws.com 
    docker push <<Account ID>>.dkr.ecr.<<AWS Region>>.amazonaws.com/flink-demo:latest
    ```

*   Deploy Flink on EKS ( files are in the install folder )

    Modify the yaml file [jobmanager-application-ha.yaml](./jobmanager-application-ha.yaml) and replace the ACCOUNT_ID, REGION, S3_BUCKET with respective values.

    Modify the yaml file [taskmanager-job-deployment.yaml](./taskmanager-job-deployment.yaml) and replace the ACCOUNT_ID, REGION with respective values.

    Modify the yaml file [flink-configuration-configmap.yaml](./flink-configuration-configmap.yaml) and replace the S3_BUCKET with the bucket name.

    ```
    chmod +x install.sh
    ./install.sh
    ``` 

    This install Flink on EKS, Autoscales the deployment, and forwards the Flink jobmanager’s web user interface port to local 8081.

    If sucessfully installed you can visit the Flink UI at http://localhost:8081, if using Cloud9 you can view thi with  http://<Preview URL>:8081 


*   Create the AWS Kinesis DataStream and Kinesis Data Generator

    Log in to AWS Console and create a Kinesis data stream name ‘ExampleInputStream’.

    Create a Kinesis Data Generator using the instructions here https://awslabs.github.io/amazon-kinesis-data-generator/web/help.html

    Use the KDG template to create and send dummy data

*   Observe

    Once data is sent Kinesis DataStream it passes on to Flink. The data is processed in Flink and the output written to files in S3. This can be observed in the Flink UI. One can observe Horrizontal and Cluster Autoscaling during this process too.    

## Cleanup

*   Run the delete.sh file. This will delete all the Apache Flink related deployments on EKS.

*   Delete the EKS cluster and the nodegroups with the following command:

    ```

    eksctl delete cluster --name flink-demo
    ```

*   Delete the Amazon S3 Access Policy with the following command:
    ```

    aws iam delete-policy --policy-arn <<POLICY ARN>>
    ```

*   Delete the Amazon S3 Bucket with the following command:
    ```
    
    aws s3 rb --force s3://<<S3_BUCKET>>
    ```

*   Delete the CloudFormation stack related to Kinesis Data Generator named ‘Kinesis-Data-Generator-Cognito-User’

*   Delete the ECR Image

*   Delete the Kinesis Data Stream.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This library is licensed under the MIT-0 License. See the LICENSE file.

