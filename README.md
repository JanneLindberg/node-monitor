# node-monitor

A lightway service to supervice a number of devices within a network. A simple ping is used to check if the
configurated nodes is available and responds within a resonable time.

The main aim is to allow a very simple monitoring of devices where installing a full fledged monitoring
agent is impractical or impossible due to hardware limitations, for example on devices like ESP8266.

There is currently no support for monitoring any service running on a node.

## Build

```sh
git clone https://github.com/JanneLindberg/node-monitor.git
lein uberjar
```

## Usage
The service is started using the following command

```sh
export PORT=<portno>
java -jar ./target/node-monitor-0.1.0-SNAPSHOT-standalone.jar
```

### Configuration
***TBD***

## REST API
The service provides a simple REST API to publish the status

### Show node status
```sh
http://localhost:<PORT>/nodes/

http://localhost:<PORT>/nodes/<nodeid>

```

### Show offline nodes
```sh
http://localhost:<PORT>/nodes/offline/
```

### Show configuration
```sh
http://localhost:<PORT>/config
```


### Contributors

[Contributors](https://github.com/JanneLindberg/node-monitor/graphs/contributors)

## License

Copyright (c) 2015 JanneLindberg

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
