# README
This is the integration test suite for the module internet technology.

Currently it contains test for the basic server.

- Server login
- Server heartbeat

## Parameters
Parameters can be configured in the testconfig.properties file

| Parameter                  | Description                                                                    |
|----------------------------|--------------------------------------------------------------------------------|
| host                       | Ip address of host to connect to                                               |
| port                       | Port where the chat client is running on                                       |
| ping_time_ms               | Time period (in ms) between ping requests from server                          |
| ping_time_ms_delta_allowed | Maximum allowed time difference (in ms) for ping request as measured by client |

## To run
1. Make sure the server is started
```
node server.js
```
2. Start the integration test by simply running this program from within IntellIj

## Notes
In JUnit Jupiter you should use TestReporter when you want to output information to the console. See H2.12 on 
https://junit.org/junit5/docs/current/user-guide/
