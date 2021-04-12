# 02. Demo - Spring Cloud Function

## Overview

The Spring development team recently (in the last few years) released a new subproject called `Spring Cloud Function`.
SCF has an interesting goal in delivering a new, functional programming model into the Spring ecosystem. You can read a
little about its goals and features [here](https://spring.io/projects/spring-cloud-function).

As SCF has matured, it is starting to show up in other downstream Spring projects like `Spring Cloud Stream`, which is
why we want to cover it here as a basic primer.

## Java's Functional APIs

In the core Java 8 programming language, there are 3 interesting interfaces that are central to Spring Cloud Function:

### java.util.function.Supplier<T>

```java

@FunctionalInterface
public interface Supplier<T> {
    T get();
}
```

This interface describes any Producer of data of type `T`. It is that simple, we can even write a basic supplier in Java
with no outside libraries like this:

```java
public static Supplier<List<String>> demoSupplier(){
    return () -> List.of("Foo","Bar","Baz");
}
```

### java.util.function.Consumer<T>

The Consumer interface is just as simple:

```java
@FunctionalInterface
public interface Consumer<T> {
    void accept(T var1);
}
```

^ Note: there an additional `andThen` method with a default implementation that we are ignoring for now.

Any `void` method that accepts `T` can be treated as a Consumer:

```java
public static void demoConsumer(String string) {
    System.out.println("demoConsumer received :: " + string);
}
```

Or even expressed as a lambda:

```java
Consumer<String> consumerLb = s -> System.out.println(s);
```

### java.util.function.Function<T, R>

Finally, the interface of a Function:

```java
@FunctionalInterface
public interface Function<T, R> {
    R apply(T var1);
}
```

An example Consumer implementation is very simple:

```java
Consumer<String> consumerLambda = System.out::println;
```

The default `accept()` method will be called directly if you pass the lambda expression into a stream operation.  Let's
look at a little larger example:

```java
package io.undertree.workshop.function;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FunctionDemo {
    public static void main(String[] args) {

        // get the List of Strings from the supplier
        var lowerStrings = demoSupplier().get();

        // apply the function to the List of Strings
        var upperStrings = upperCase().apply(lowerStrings);

        // send each string to the consumer
        Consumer<String> consumerFn = FunctionDemo::demoConsumer;
        //Consumer<String> consumerLb = System.out::println;
        upperStrings.forEach(consumerFn);
    }

    // a functional producer of a List of Strings
    public static Supplier<List<String>> demoSupplier() {
        return () -> List.of("foo", "bar", "baz");
    }

    // a functional function that takes a List of Strings and returns a List of Strings
    public static Function<List<String>, List<String>> upperCase() {
        return s -> s.stream().map(String::toUpperCase).collect(Collectors.toList());
    }

    // a functional consumer of a String
    public static void demoConsumer(String string) {
        System.out.println("demoConsumer received :: " + string);
    }
}
```

^ Just a reminder, there is no Spring code here, this is 100% pure core Java.  Even though the logic is somewhat
trivial, you can see that the application of these interfaces can be quite expressive.

Now that we understand these basic (primitive?) interfaces we can construct almost any chain of logic that can be
orchestrated into more complex business logic.

## Lab - Build a Simple Spring Boot Application

Go to [start.spring.io](https://start.spring.io).

Select the defaults for a Spring Boot application and add a single dependency:

- `function`

Generate the application and import it into your IDE.

If you try to run the application you'll notice that it starts up and shuts down with no errors.  This is probably not
what you are used to from a Spring Boot application - this is a very basic Spring context with Spring Cloud Function
enabled.

To demo something more interesting we need to add a dependency by hand:

In the project `pom.xml`  add this dependency (you can use this to replace the default `spring-cloud-function-context`
dependency as it is a transitive dependency):

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-function-webflux</artifactId>
</dependency>
```

(or in your `build.gradle`)

```groovy
implementation 'org.springframework.cloud:spring-cloud-starter-function-webflux'
```

Refresh you dependencies and re-run the application, this time you should see something like this in your logs:

```text
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v2.4.2)

2021-02-28 15:36:46.266  INFO 22211 --- [           main] i.u.workshop.function.DemoFunctionApp    : Starting DemoFunctionApp using Java 11.0.9.1 on ... with PID 22211 (... started by ... in ...)
2021-02-28 15:36:46.268  INFO 22211 --- [           main] i.u.workshop.function.DemoFunctionApp    : No active profile set, falling back to default profiles: default
2021-02-28 15:36:46.773  INFO 22211 --- [           main] o.s.c.f.web.flux.FunctionHandlerMapping  : FunctionCatalog: org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry@6ff6efdc
2021-02-28 15:36:46.893  INFO 22211 --- [           main] o.s.b.web.embedded.netty.NettyWebServer  : Netty started on port 8080
2021-02-28 15:36:46.902  INFO 22211 --- [           main] i.u.workshop.function.DemoFunctionApp    : Started DemoFunctionApp in 0.819 seconds (JVM running for 1.179)
```

This looks more familiar!

But how does this relate to Spring Cloud Functions?  Let's take what we learned earlier about the functional Java
interfaces and create a few `@Bean` methods (just add them to the main class):

```java
    @Bean
    public Supplier<String> greetings() {
        return () -> "Hello at " + Instant.now();
    }

    @Bean
    public Function<String, String> toUppercase() {
        return value -> value.toUpperCase();
    }

    @Bean
    public Consumer<String> logMessage() {
        return System.out::println;
    }
```

Restart the application.

Let's quickly review what we've done here.  We created 3 functional "beans" that represent a Supplier, a Consumer and a
Function.  So how do you think this will all work from the web client perspective?

We can validate that these functions are working correctly by testing with `curl` (or `httpie` depending on your
preference):

```shell
$ curl localhost:8080
```

As expected, you should see an error json response.  We don't have any endpoints defined on the default path (yet).  But
what if we try to request one of the methods directly?

```shell
$ curl localhost:8080/greetings
Hello at 2021-02-28T20:58:01.240611Z%
```

Wow!  What's going on here?  There aren't any REST endpoints!  How is this even working?!?!

This is part of the built-in Spring Cloud Function behavior with the web/webfux project.  At start-up, Spring Boot is
using Spring Cloud Function to find and automatically register functional Beans with the default request handler.  This
automatic behavior is also using the Bean name to create an implied endpoint.

We can further explore the other endpoints:

```shell
$ curl -H "Content-Type: text/plain" localhost:8080/toUppercase -d "this is a test"

THIS IS A TEST 
```

This demonstrates that Functions represent POST operations.

Finally, we can invoke the Consumer we created:

```shell
$ curl -H "Content-Type: text/plain" localhost:8080/logMessage -d "this is a log test"
```

If you look into the application log, you should now see a message printed to standard out.

You may also register whole functional classes, the classes just has to implement one of the 3 functional interfaces
and then configure Spring where to find them:

```yaml
spring:
  cloud:
    function:
      scan:
        packages: "<your namespace here>"
```

If you have any functional classes in this namespace, they will be picked up and registered with the Spring Web context
just like with the other `@Bean` methods... only this time, ***the functional classes have no Spring code in them at
all***.

We can also compose one or more functions together, try this:

```shell
$ curl -H "Content-Type: text/plain" localhost:8080/toUppercase,logMessage -d "this is a compose test"
```

In this case, the output of the `toUppercase` Function is chained to the `logMessage` Consumer.  The standard output of
your application should have the message now appear in uppercase.

Finally, you can define this composition as part of the application itself and have the configured behavior exposed on
the default (/) path.  Update the application.properties/yml:

```yaml
spring:
  cloud:
    function:
      definition: toUppercase|logMessage
```

When you restart the application, use curl again to POST another message to the default path:

```shell
$ curl -H "Content-Type: text/plain" localhost:8080 -d "this is a default path test message"
```

Nice!

## Summary 

While this slight diversion in Spring Cloud Function is important since much of the programming model of Spring Cloud
Stream has changed to support this functional interface model.  In fact, you will probably see this programming model
show up in more places in the Spring ecosystem, so it is good to get familiar with - particularly if you are interested
in serverless platforms like [AWS Lambda](https://aws.amazon.com/lambda/) or [Knative](https://knative.dev/).

## Extra Credit

- Implement a new Functional class to perform a "ToLowercase" operation and confirm that you can `curl` a valid response.


- Review the Spring Cloud Function [docs](https://cloud.spring.io/spring-cloud-function/reference/html/spring-cloud-function.html).


- Write a test case for your functions.