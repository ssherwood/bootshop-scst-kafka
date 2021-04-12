# 01. Introductions

## Goal(s)

The goal of this workshop is to provide an overview of the current state of the [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream)
project and build a few sample application demonstrating the features of the project.  Specifically, this workshop
material focuses on the new functional programming model introduced in SCSt 3.x.

## Overview

The Spring Cloud Stream project is a mature subproject of the Spring Cloud umbrella.  SCSt was designed to make it easier
for developers to write and manage streaming applications and to introducing a layer of separation between the
underlying streaming platform.  This will result in significant portions of business logic that is completely
decoupled from the underlying APIs and allow for greater portability.

From the SCSt docs:

```text
Spring Cloud Stream is a framework for building highly scalable event-driven microservices connected with shared
messaging systems.

The framework provides a flexible programming model built on already established and familiar Spring idioms and best
practices, including support for persistent pub/sub semantics, consumer groups, and stateful partitions.
```

In theory, most if not all underlying streaming platforms will need to provide similar semantic solutions that can be
generalized by a higher-level framework.

## Spring Cloud Stream (The Framework)

Spring Cloud Stream is broken up into two distinct layers: the SCSt framework itself, and Binders.  Binders provide the
underlying implementation of the streaming platform being leveraged (e.g. RabbitMQ, Kafka, Solace PubSub, etc.).
Developers should be familiar with this design pattern as it has been frequently implemented in other data persistence
platforms like JDBC or JPA.

SCSt allows for multiple binder implementations to be configured within the same application as integration patterns
often may integrate streams of events from one platform to another.

## Core Concepts

TODO define these concepts:

- [Binders](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#spring-cloud-stream-overview-binders)
- [Bindings](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream.html#binding-properties)
- Channels
  - [MessageChannel](https://docs.spring.io/spring-integration/reference/html/overview.html#overview-components-channel)
  - [errorChannel](https://docs.spring.io/spring-integration/reference/html/error-handling.html)
  - [integrationHeaderChannelRegistry](https://docs.spring.io/spring-integration/docs/current/reference/html/index-single.html#header-channel-registry)
- Schedulers
  - [taskScheduler](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling)
-  MessageHandler