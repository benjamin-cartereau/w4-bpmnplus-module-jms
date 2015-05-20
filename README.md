JMS module for W4 BPMN+
=======================

Summary
-------

This is a JMS module implementation for W4 BPMN+ (9.1+) that allows to instantiate a process when receiving a JMS message.
Several endpoints (destination and selector) can be configured. For each one a different process definition (to instantiate) can be associated.


Download
--------

The package should be available soon from the W4 store.
 

Installation
------------

### Extraction

Extract the package, either zip or tar.gz, at the root of a W4 BPMN+ Engine installation. It will create the necessary entries into `modules` subdirectory of W4 BPMN+ Engine.

### Configuration

Locate the configuration dir `W4BPMPLUS_HOME/modules/bpmnplus-module-jms/conf` 
In this directory, 4 files should be created:
 - jndi.properties
 - jms.properties
 - configuration.properties
 - log4j2.xml
To create these files, you can rely on associated samples (*-sample.*).

#### JNDI

Into the file "jndi.properties", at least 2 properties must be specified:
 - java.naming.factory.initial : factory fully qualified name 
 - java.naming.provider.url : JNDI provider URL
Specific JNDI provider properties can also be added.

#### JMS

Into the file "jms.properties", at least 1 property must be specified:
 - jms.connection.factory.name : JNDI name of the connection factory

#### Configuration

Into the file "configuration.properties" endpoints should be configured :

 - module.jms.principal.login : main principal login used to authenticate against the engine
 - module.jms.principal.password : main principal password used to authenticate against the engine
 - module.jms.endpoints : list of endpoints ids
  - module.jms.endpoint.[endpoint id].destination : queue name
  - module.jms.endpoint.[endpoint id].selector (non mandatory) : [expression](http://docs.oracle.com/cd/E19798-01/821-1841/bncer/index.html "JMS Message Selectors") based on a subset of the SQL92 conditional expression syntax
  - module.jms.endpoint.[endpoint id].principal.login (non mandatory) : to override main principal or set a specific one
  - module.jms.endpoint.[endpoint id].principal.password (non mandatory) : -
  - module.jms.endpoint.[endpoint id].bpmn.definition_identifier : id of the process definition
  - module.jms.endpoint.[endpoint id].bpmn.collaboration_identifier (non mandatory) : if the process is part of a collaboration process
  - module.jms.endpoint.[endpoint id].bpmn.process_identifier : id of the process
  - module.jms.endpoint.[endpoint id].bpmn.process_instance_name_prefix (non mandatory) : should the process instances name have a prefix
  - module.jms.endpoint.[endpoint id].bpmn.data_entry_id : id of the data entry to set. The data entry's type can be string or XSD.
  - module.jms.endpoint.[endpoint id].mapping * : none (default) or json. Make it possible to pass an object serialized as JSON (TextMessage) and automatically process the mapping before setting the data entry value.

* To be able to process the mapping (string->object), the module need 2 things:
 - The fully qualified class name of the object : it should be specified by the message sender through the JMS property named "ClassName"
 - The class that could be encapsulated into a jar file and dropped into ext folder.

When using 

#### Log4j2

This is a standard [Log4j 2](http://logging.apache.org/log4j/2.x/ "Log4j 2.x") configuration file.

### JMS Broker libraries

Eventually, to allow the module to connect to the JMS broker (through the JNDI provider), you will have to add associated libraries into the ext dir : `W4BPMPLUS_HOME/modules/bpmnplus-module-jms/lib/ext`.
For example when using Active MQ 5.11, you will have to add those libraries:
 - activemq-client-5.11.1.jar
 - geronimo-j2ee-management_1.1_spec-1.0.1.jar

Usage
-----

When restarting W4 BPMN+ Engine, module will be started (logs may be produced depending on log level) and listening to messages.


License
-------

Copyright (c) 2015, Benjamin Cartereau

This project is licensed under the terms of the MIT License (see LICENSE file)

Ce projet est licencié sous les termes de la licence MIT (voir le fichier LICENSE)
