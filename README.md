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

 - module.jms.principal.login
 - module.jms.principal.password
 - module.jms.endpoints : list of endpoints ids
  - module.jms.endpoint.[endpoint id].destination : queue name
  - module.jms.endpoint.[endpoint id].selector (non mandatory)
  - module.jms.endpoint.[endpoint id].principal.login (non mandatory) : to override main principal or set a specific one
  - module.jms.endpoint.[endpoint id].principal.password (non mandatory)
  - module.jms.endpoint.[endpoint id].bpmn.definition_identifier : process definition id
  - module.jms.endpoint.[endpoint id].bpmn.collaboration_identifier (non mandatory)
  - module.jms.endpoint.[endpoint id].bpmn.process_identifier : process id
  - module.jms.endpoint.[endpoint id].bpmn.process_instance_name_prefix : should the process instance have a name prefix
  - module.jms.endpoint.[endpoint id].bpmn.data_entry_id : id of the data entry to set
  - module.jms.endpoint.[endpoint id].mapping : should the data entry be mapped (for example retrieved an object as a json one through a JMS TextMessage)

#### Log4j2

This is a standard [Log4j 2](http://logging.apache.org/log4j/2.x/ "Log4j 2.x") configuration file.

Usage
-----

When restarting W4 BPMN+ Engine, module will be started (logs may be produced depending on log level) and listening to messages.


License
-------

Copyright (c) 2015, Benjamin Cartereau

This project is licensed under the terms of the MIT License (see LICENSE file)

Ce projet est licencié sous les termes de la licence MIT (voir le fichier LICENSE)
