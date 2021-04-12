# README

Reference: https://github.com/bitnami/bitnami-docker-kafka

1. Update your /etc/hosts with your Docker host IP (ip a show docker0).  Ex:

```text
172.17.0.1 kafka1.test.local
172.17.0.1 kafka2.test.local
172.17.0.1 kafka3.test.local
```

2. Map the KAFKA_DATA to a folder of your choosing and export it (example):

```shell
$ export KAFKA_DATA=$HOME/Repos/data/kafka
```

3. Review https://docs.bitnami.com/tutorials/work-with-non-root-containers/

```shell
$ mkdir -p $KAFKA_DATA/zookeeper $KAFKA_DATA/kafka1 $KAFKA_DATA/kafka2 $KAFKA_DATA/kafka3
$ sudo chown -R 1001:root $KAFKA_DATA
```

## Additional Information
- https://rmoff.net/2018/08/02/kafka-listeners-explained/
