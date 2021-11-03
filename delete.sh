kubectl delete -f flink-configuration-configmap.yaml
kubectl delete -f jobmanager-application-ha.yaml
kubectl delete -f taskmanager-job-deployment.yaml
kubectl delete -f jobmanager-rest-service.yaml
kubectl delete -f jobmanager-service.yaml
kubectl delete cm --selector='configmap-type=high-availability'
kubectl delete -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.4.1/components.yaml
kubectl delete horizontalpodautoscaler.autoscaling/flink-taskmanager