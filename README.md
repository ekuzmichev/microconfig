# Microconfig overview and features

[![Build Status](https://travis-ci.com/microconfig/microconfig.svg?branch=master)](https://travis-ci.com/microconfig/microconfig)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Microconfig is intended to make it easy and convenient to manage configuration for microservices (or just for big amount of services) and reuse common part.

If your project consists of tens or hundreds services you have to:
* Keep configuration for each service, ideally separately from code.
* Configuration for different services can have common and specific parts. Also configuration for the same service on different environments can have common and specific parts as well.
* Common part for different services (or for one service on different environments) should not be copy-pasted and must be easy to reuse.
* It must be easy to understand how result file is generated and based on what placeholders are resolved. 
* Some configuration properties must be dynamic (calculated using expression language) using other properties.

Microconfig is written in Java, but it designed to be used with systems written in any language. Microconfig just describes format of base configuration, syntax for placeholders, includes, excludes, overrides, expression language for dynamic properties and engine than can build it to plain *.properties or *.yaml. Also it can resolve placeholders in arbitrary template files and show diff between config releases.

Configuration can be built during deploy phase and result plain config files can be copied to filesystem, where your services can access it directly(for instance, Spring Boot can read configuration from *.properties), or you can distribute result configuration using any config servers (like [Spring cloud config server](https://spring.io/projects/spring-cloud-config))

# How to keep configuration
It’s a good practice to keep service configuration separated from code. It allows not to rebuild your services any time configuration is changed and use the same service artifacts (for instance, *.jar) for all environments, because it doesn’t contain any env specific configuration. Configuration can be updated even in runtime without service' source code changes.

So the best way to follow this principle is to have dedicated repository for configuration in your favorite version control system.  You can store configuration for all microservices in one repository to make it easy to reuse common part and be sure common part for services is consistent. 

# Basic folder layout
Let’s see basic folder layout that you can keep in a dedicated repository.

For every service you have to create folder with unique name(name of the service). In service directory we will keep common and env specific configuration.

So let’s image we have 4 microservices: order service, payment service,  service-discovery and api-gateway. To make it easy to manage we can group services by layers: 'infra' for infrastructure services and 'core' for our business domain services. The result layout will look like:

```
repo
└───core  
│    └───orders
│    └───payments
│	
└───infra
    └───service-discovery
    └───api-gateway
```

# Service configuration types

It convenient to have different kinds of configuration and keep it in different files:
* Process configuration (configuration that is used (by deployment tools) to start your service, like memory limit, VM params, etc. 
* Application configuration (configuration that your service reads after startup and use in runtime)
* OS ENV variables
* Lib specific templates (for instance, your logger specific descriptor (logback.xml), kafka.conf, cassandra.yaml, etc)
* Static files/scripts to run before/after your service start
* Secrets configuration (Note, you should not store in VCS any sensitive information, like passwords. In VCS you can store references(keys) to passwords, and keep password in special secured stores(like Vault) or at least in encrypted files on env machines)

# Service configuration files

Inside service folder you can create configuration in key=value format. 

Let’s create basic application and process configuration files for each service. 
Microconfig treats *.properties like application properties and *.proc like process properties.
You can split configuration among several files, but for simplity we will create single application.properties and process.proc for each service. Anyway after configuration build for each service for each config type a single result file will be generated despite amount of base source files.


```
repo
└───core  
│    └───orders
│    │   └───application.properties
│    │   └───process.proc
│    └───payments
│        └───application.properties
│        └───process.proc
│	
└───infra
    └───service-discovery
    │   └───application.properties
    │   └───process.proc
    └───api-gateway
        └───application.properties
        └───process.proc
```

Inside process.proc we will store configuration that describes what is your service and how to run it (Your config files can have other properties, so don't pay attention on concrete values).

**orders/process.proc**
```*.properties
    artifact=org.example:orders:19.4.2 # artifact in maven format groupId:artifactId:version
    java.main=org.example.orders.OrdersStarter # main class to run
    java.opts.mem=-Xms1024M -Xmx2048M -XX:+UseG1GC -XX:+PrintGCDetails -Xloggc:logs/gc.log # vm params
```
**payments/process.proc**
```*.properties
    artifact=org.example:payments:19.4.2 # partial duplication
    java.main=org.example.payments.PaymentStarter
    java.opts.mem=-Xms1024M -Xmx2048M -XX:+UseG1GC -XX:+PrintGCDetails -Xloggc:logs/gc.log # duplication
    instance.count=2
```
**service-discovery/process.proc**
```*.properties
    artifact=org.example.discovery:eureka:19.4.2 # partial duplication         
    java.main=org.example.discovery.EurekaStarter
    java.opts.mem=-Xms1024M -Xmx2048M # partial duplication         
```

As you can see we already have some small copy-paste (all services have 19.4.2 version, two of them have the same java.ops params).  Configuration duplication as bad as code one. We will see further how to do it better.

Let's see how application properties can look like. In comments we note what can be improved.

**orders/application.properties**
```*.properties
    server.port=9000
    application.name=orders # better to get name from folder
    orders.personalRecommendation=true
    statistics.enableExtendedStatistics=true
    service-discovery.url=http://10.12.172.11:6781 # are you sure url is consistent with eureka configuration?
    eureka.instance.prefer-ip-address=true  # duplication        
    datasource.minimum-pool-size=2  # duplication
    datasource.maximum-pool-size=10    
    datasource.url=jdbc:oracle:thin:@172.30.162.31:1521:ARMSDEV  # partial duplication
    jpa.properties.hibernate.id.optimizer.pooled.prefer_lo=true  # duplication
```
**payments/application.properties**
```*.properties
    server.port=8080
    application.name=payments # better to get name from folder
    payments.booktimeoutInMs=900000 # how long in min ?
    payments.system.retries=3
    consistency.validateConsistencyIntervalInMs=420000 # difficult to read. how long in min ?
    service-discovery.url=http://10.12.172.11:6781 # are you sure url is consistent with eureka configuration?
    eureka.instance.prefer-ip-address=true  # duplication            
    datasource.minimum-pool-size=2  # duplication
    datasource.maximum-pool-size=5    
    datasource.url=jdbc:oracle:thin:@172.30.162.127:1521:ARMSDEV  # partial duplication
    jpa.properties.hibernate.id.optimizer.pooled.prefer_lo=true # duplication
```
**service-discovery/application.properties**
```*.properties
    server.port=6781
    application.name=eureka
    eureka.client.fetchRegistry=false
    eureka.server.eviction-interval-timer-in-ms=15000 # difficult to read
    eureka.server.enable-self-preservation=false    
```


The first bad thing - application files contain duplication. Also you have to spend some time to understand application’s dependencies or it structure. For instance, payments service contains settings for 1) service-discovery client,  2)for oracle db and 3)application specific. Of course you can separate group of settings by empty line. But we can do it more readable and understandable.


# Better config structure using #include
Our services have common configuration for service-discovery and database. To make it easy to understand service's dependencies, let’s create folders for service-discovery-client and oracle-client and specify links to these dependencies from core services.

```
repo
└───common
|    └───service-discovery-client 
|    | 	 └───application.properties
|    └───oracle-client
|        └───application.properties
|	
└───core  
│    └───orders
│    │   ***
│    └───payments
│        ***
│	
└───infra
    └───service-discovery
    │   ***
    └───api-gateway
        ***
```
**service-discovery-client/application.properties**
```*.properties
service-discovery.url=http://10.12.172.11:6781 # are you sure url is consistent with eureka configuration?
eureka.instance.prefer-ip-address=true 
```

**oracle-client/application.properties**
```*.properties
datasource.minimum-pool-size=2  
datasource.maximum-pool-size=5    
datasource.url=jdbc:oracle:thin:@172.30.162.31:1521:ARMSDEV  
jpa.properties.hibernate.id.optimizer.pooled.prefer_lo=true
```

And replace explicit configs with includes

**orders/application.properties**
```*.properties
    #include service-discovry-client
    #include oracle-db-client
    
    server.port=9000
    application.name=orders # better to get name from folder
    orders.personalRecommendation=true
    statistics.enableExtendedStatistics=true    
```

**payments/application.properties**
```*.properties
    #include service-discovry-client
    #include oracle-db-client
    
    server.port=8080
    application.name=payments # better to get name from folder
    payments.booktimeoutInMs=900000 # how long in min ?
    payments.system.retries=3
    consistency.validateConsistencyIntervalInMs=420000 # difficult to read. how long in min ?    
```
Some problems still here, but we removed duplication and made it easy to understand service's dependencies.

You can override any properties from your dependencies.
Let's override order's connection pool size.

**orders/application.properties**
```*.properties        
    #include oracle-db-client
    datasource.maximum-pool-size=10
    ***    
```

Nice. But order-service has small part of its db configuration(pool-size), it not that bad, but we can make config semantically better.
Also as you could notice order and payment services have different ip for oracle.

order: datasource.url=jdbc:oracle:thin:@172.30.162.<b>31</b>:1521:ARMSDEV  
payment: datasource.url=jdbc:oracle:thin:@172.30.162.<b>127</b>:1521:ARMSDEV  
And oracle-client contains settings for .31.

Of course you can override datasource.url in payment/application.properties. But this overridden property will contain duplication of another part of jdbc url and you will get all standard copy-paste problems. We would like to override only part of property. 

Also it better to create dedicated configuration for order db and payment db. Both db configuration will include common-db config and override ip part of url.  After that we will migrate datasource.maximum-pool-size from orders service to order-db, so order service will contains only links to it dependecies and service specific configs.

Let’s refactor.
```
repo
└───common
|    └───oracle
|        └───oracle-common
|        |   └───application.properties
|        └───order-db
|        |   └───application.properties
|        └───payment-db
|            └───application.properties
```

**oracle-common/application.properties**
```*.properties
datasource.minimum-pool-size=2  
datasource.maximum-pool-size=5    
jpa.properties.hibernate.id.optimizer.pooled.prefer_lo=true
```
**orders-db/application.properties**
```*.properties
    #include oracle-common
    datasource.maximum-pool-size=10
    datasource.url=jdbc:oracle:thin:@172.30.162.31:1521:ARMSDEV #partial duplication
```
**payment-db/application.properties**
```*.properties
    #include oracle-common
    datasource.url=jdbc:oracle:thin:@172.30.162.127:1521:ARMSDEV #partial duplication
```

**orders/application.properties**
```*.properties
    #include order-db
    ***
```

**payments/application.properties**
```*.properties
    #include payment-db
```

# Env specific properties
Microconfg allows specifying env specific properties (add/remove/override). For instance you want to increase connection-pool-size for dbs and increase amount of memory for prod env.
To add/remove/override properties for env, you can create application.**${ENVNAME}**.properties file in config folder. 

Let's override connection pool connection size for dev and prod and add one new param for dev. 

```
order-db
└───application.properties
└───application.dev.properties
└───application.prod.properties
```

**orders-db/application.dev.properties**
```*.properties   
    datasource.maximum-pool-size=15    
```

**orders-db/application.prod.properties**
```*.properties   
    datasource.maximum-pool-size=50    
```

Also you can declare common properties for several environments on a single file.  You can use following file name pattern: application.**${ENV1.ENV2.ENV3...}**.properties
Let's create common props for dev, dev2 and test envs.

```
order-db
└───application.properties
└───application.dev.properties
└───application.dev.dev2.test.properties
└───application.prod.properties
```

**orders-db/application.dev.dev2.test.properties**
```*.properties   
    hibernate.show-sql=true    
```

When you build properties for specific env(for example 'dev') Microconfig will collect properties from:
* application.properties 
* then add/override properties from application.dev.{anotherEnv}.properties.
* then add/override properties from application.dev.properties.

# Placeholders

Instead of copy-paste value of some property Microconfig allows to placeholder to this value. 

Let's refactor service-discovery-client config.

Initial:

**service-discovery-client/application.properties**
```*.properties
service-discovery.url=http://10.12.172.11:6781 # are you sure host and port are consistent with SD configuration? 
```
**service-discovery/application.properties**
```*.properties
server.port=6761 
```

Refactored:

**service-discovery-client/application.properties**
```*.properties
service-discovery.url=http://${service-discovery@ip}:${service-discovery@server.port} # are you sure host and port are consistent with SD configuration? 
```
**service-discovery/application.properties**
```*.properties
server.port=6761
ip=10.12.172.11 
```

So if you change service-discovery port, all dependent services will get this update.

Microconfig has another approach to store service's ip. We will discuss it later. For now it better to set ip property inside service-discovery config file. 

Microconfig syntax for placeholders ${**componentName**@**propertyName**}. Microconfig forces to specify component name(folder). This syntax match better than just prop name 
(like ${serviceDiscoveryPortName}), because it makes it obvious based on what placeholder will be resolved and where to find initial placeholder value.

Let's refactor oracle db config using placeholders and env specific overrides.

Initial:

**oracle-common/application.properties**
```*.properties    
    datasource.maximum-pool-size=10
    datasource.url=jdbc:oracle:thin:@172.30.162.31:1521:ARMSDEV 
```   

Refactored:

**oracle-common/application.properties**
```*.properties    
    datasource.maximum-pool-size=10
    datasource.url=jdbc:oracle:thin:@${this@host}:1521:${this@oracle.sid}
    oracle.host=172.30.162.20    
    oracle.sid=ARMSDEV
```
**oracle-common/application.uat.properties**
```*.properties    
    oracle.host=172.30.162.80
```    
    
**oracle-common/application.prod.properties**
```*.properties    
    oracle.host=10.17.14.18    
    oracle.sid=ARMSDEV    
```        

As you can see using placeholders we can override not the whole property but only part of it. 

If you want to declare temp properties that will be used for placeholders and you don't want them to be included into result config file, you can declare them with #var keyword.

**oracle-common/application.properties**
```*.properties
    datasource.url=jdbc:oracle:thin:@${this@host}:1521:${this@oracle.sid}
    #var oracle.host=172.30.162.20    
    #var oracle.sid=ARMSDEV
```
**oracle-common/application.uat.properties**
```*.properties    
    #var oracle.host=172.30.162.80
```  

This approach works with includes as well. You can #include oracle-common and then override oracle.host, and datasource.url will be resolved based of overridden value.

In the example below after build datasource.url=jdbc:oracle:thin:@**100.30.162.80**:1521:ARMSDEV
 
**orders-db/application.dev.properties** 
```*.properties   
     #include oracle-common    
     #var oracle.host=100.30.162.80                 
```  

# Profiles and explicit env name for placeholders
..todo write doc
# Expression language
..todo write doc
# Arbitrary template files
..todo write doc
# Post config build callbacks
..todo write doc