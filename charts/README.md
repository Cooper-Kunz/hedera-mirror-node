# Helm Chart

Installs the Hedera Mirror Node Helm wrapper chart. This chart will install the three mirror node components:

- [Hedera Mirror Importer](hedera-mirror-importer)
- [Hedera Mirror GRPC API](hedera-mirror-importer)
- [Hedera Mirror REST API](hedera-mirror-importer)

## Requirements

- [Helm 3](https://helm.sh)
- [Kubernetes 1.17+](https://kubernetes.io)

## Install

To install the wrapper chart with a release name of `mirror`:

```shell script
$ helm upgrade --install mirror charts/hedera-mirror
```

## Uninstall

To remove all the Kubernetes components associated with the chart and delete the release:

```shell script
$ helm delete mirror
```

The above command does not delete any of the underlying persistent volumes. To delete all the data associated with this release:

```shell script
$ kubectl delete $(kubectl get pvc -o name)
```

## Troubleshooting

To troubleshoot a pod, you can view its log and describe the pod to see its status. See the [kubectl](https://kubernetes.io/docs/reference/kubectl/overview/) for more commands.

```shell script
$ kubectl describe pod mirror-importer-0
$ kubectl logs -f --tail=100 mirror-importer-0
$ kubectl logs -f --prefix --tail=10 -l app.kubernetes.io/name=importer
```

To change application properties without restarting, you can create a [ConfigMap](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#create-configmaps-from-files) named `hedera-mirror-grpc` or `hedera-mirror-importer` and supply an `application.yaml` or `application.properties`. Note that some properties that are used on startup will still require a restart.

```shell script
$ echo "logging.level.com.hedera.mirror.grpc=TRACE" > application.properties
$ kubectl create configmap hedera-mirror-grpc --from-file=application.properties
```

Dashboard and metrics can be viewed via [Grafana](https://grafana.com). To access, get the external IP and open it in a browser:

```shell script
$ kubectl get service grafana
NAME      TYPE           CLUSTER-IP       EXTERNAL-IP      PORT(S)        AGE
grafana   LoadBalancer   10.102.234.103   10.102.234.103   80:30125/TCP   38m
```

To connect to the database and run queries:

```shell script
$ kubectl exec -it postgresql-postgresql-0 -c postgresql -- psql -d mirror_node -U mirror_node
```