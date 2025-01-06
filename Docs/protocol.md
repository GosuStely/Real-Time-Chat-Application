# Protocol description

This client-server protocol describes the following scenarios:
- Setting up a connection between client and server.
- Broadcasting a message to all connected clients.
- Periodically sending heartbeat to connected clients.
- Disconnection from the server.
- Handling invalid messages.

In the description below, `C -> S` represents a message from the client `C` is send to server `S`. When applicable, `C` is extended with a number to indicate a specific client, e.g., `C1`, `C2`, etc. The keyword `others` is used to indicate all other clients except for the client who made the request. Messages can contain a JSON body. Text shown between `<` and `>` are placeholders.

The protocol follows the formal JSON specification, RFC 8259, available on https://www.rfc-editor.org/rfc/rfc8259.html

# 1. Establishing a connection

The client first sets up a socket connection to which the server responds with a welcome message. The client supplies a username on which the server responds with an OK if the username is accepted or an ERROR with a number in case of an error.
_Note:_ A username may only consist of characters, numbers, and underscores ('_') and has a length between 3 and 14 characters.

## 1.1 Happy flow

Client sets up the connection with server.
```
S -> C: READY {"version": "<server version number>"}
```
- `<server version number>`: the semantic version number of the server.

After a while when the client logs the user in:
```
C -> S: ENTER {"username":"<username>"}
S -> C: ENTER_RESP {"status":"OK"}
```

- `<username>`: the username of the user that needs to be logged in.
      To other clients (Only applicable when working on Level 2):
```
S -> others: JOINED {"username":"<username>"}
```

## 1.2 Unhappy flow
```
S -> C: ENTER_RESP {"status":"ERROR", "code":<error code>}
```      
Possible `<error code>`:

| Error code | Description                              |
|------------|------------------------------------------|
| 5000       | User with this name already exists       |
| 5001       | Username has an invalid format or length |      
| 5002       | Already logged in                        |

# 2. Broadcast message

Sends a message from a client to all other clients. The sending client does not receive the message itself but gets a confirmation that the message has been sent.

## 2.1 Happy flow

```
C -> S: BROADCAST_REQ {"message":"<message>"}
S -> C: BROADCAST_RESP {"status":"OK"}
```
- `<message>`: the message that must be sent.

Other clients receive the message as follows:
```
S -> others: BROADCAST {"username":"<username>","message":"<message>"}   
```   
- `<username>`: the username of the user that is sending the message.

## 2.2 Unhappy flow

```
S -> C: BROADCAST_RESP {"status": "ERROR", "code": <error code>}
```
Possible `<error code>`:

| Error code | Description            |
|------------|------------------------|
| 6000       | User is not logged in  |

# 3. Heartbeat message

Sends a ping message to the client to check whether the client is still active. The receiving client should respond with a pong message to confirm it is still active. If after 3 seconds no pong message has been received by the server, the connection to the client is closed. Before closing, the client is notified with a HANGUP message, with reason code 7000.

The server sends a ping message to a client every 10 seconds. The first ping message is send to the client 10 seconds after the client is logged in.

When the server receives a PONG message while it is not expecting one, a PONG_ERROR message will be returned.

## 3.1 Happy flow

```
S -> C: PING
C -> S: PONG
```     

## 3.2 Unhappy flow

```
S -> C: HANGUP {"reason": <reason code>}
[Server disconnects the client]
```      
Possible `<reason code>`:

| Reason code | Description      |
|-------------|------------------|
| 7000        | No pong received |    

```
S -> C: PONG_ERROR {"code": <error code>}
```
Possible `<error code>`:

| Error code | Description         |
|------------|---------------------|
| 8000       | Pong without ping   |    

# 4. Termination of the connection

When the connection needs to be terminated, the client sends a bye message. This will be answered (with a BYE_RESP message) after which the server will close the socket connection.

## 4.1 Happy flow
```
C -> S: BYE
S -> C: BYE_RESP {"status":"OK"}
[Server closes the socket connection]
```

Other, still connected clients, clients receive:
```
S -> others: LEFT {"username":"<username>"}
```

## 4.2 Unhappy flow

- None

# 5. Invalid message header

If the client sends an invalid message header (not defined above), the server replies with an unknown command message. The client remains connected.

Example:
```
C -> S: MSG This is an invalid message
S -> C: UNKNOWN_COMMAND
```

# 6. Invalid message body

If the client sends a valid message, but the body is not valid JSON, the server replies with a pars error message. The client remains connected.

Example:
```
C -> S: BROADCAST_REQ {"aaaa}
S -> C: PARSE_ERROR
```
# 7. Get a list of all connected clients

Allows a client to request a list of all currently connected clients. The server responds with a list containing the usernames of all connected clients.

## 7.1. Happy flow
```
C1 -> S: LIST_REQ
S -> C1: LIST_RESP {"clients": ["<C2_username>", "<C3_username>", "<C4_username>", ...]}
```
`<C2_username>`: the username of client 2
`<C3_username>`: the username of client 3
`<C4_username>`: the username of client 4


## 7.2. Unhappy flow

```
S -> C: LIST_RESP {"status":"ERROR", "code":<error_code>}
```
Possible `<error code>`:

| Error code | Description                |
|------------|----------------------------|
| 9000       | User is not logged in      | 

# 8. Send private message from a client to another client

## 8.1. Happy flow

C1 sends a private message request to the server, specifying the recipient's username (C2) and the message content

```
C1 -> S: PRIVATE_MSG_REQ {"receiver":"<C2_username>", "message":"<message>"}
S -> C1: PRIVATE_MSG_RESP {"status":"OK"}
```

Client B receives the private message as follows:

```
S -> C2: PRIVATE_MSG {"sender":"<C1_username>", "message":"<message>"}
```

## 8.2. Unhappy flow
```
S -> C1: PRIVATE_MSG_RESP {"status":"ERROR", "code":<error_code>}
```
Possible `<error code>`:

| Error code | Description                        |
|------------|------------------------------------|
| 10001      | User is not logged in              | 
| 10002      | No receiver found                  | 
| 10003      | Can't send private message to self | 

# 9. Rock, Paper, Scissors
## 9.1. A player initiate the game
```
C1 -> S: GAME_START_REQ {"receiver": "<C2_username>"}
```

### 9.1.1. Happy flow
```
S -> C1: GAME_START_RESP {"status": "OK"}
S -> C2: GAME_INVITE {"sender": "<C1_username>"}
```

### 9.1.2. Unhappy flow
```
S -> C1: GAME_START_RESP {"status": "ERROR", "code": <error code>}
```
Possible `<error code>`:

| Error code | Description                                                   |
|------------|---------------------------------------------------------------|
| 11001      | No receiver found                                             | 
| 11002      | Can't send game request to self                               | 

In case there's an ongoing game between other players (referred as `C3` and `C4`):
```
S -> C1: GAME_START_RESP {"status": "ERROR", "code": <error code>, "player1": "<C3_username>", "player2": "<C4_username>"}
```
Possible `<error code>`:

| Error code | Description                                                           |
|------------|-----------------------------------------------------------------------|
| 11003      | A game is already ongoing between `<C3_username>` and `<C4_username>` | 


## 9.2. Receiver accepts the invitation
The receiver responds to the invitation (accept or decline).
### 9.2.1. Happy flow
If Client 2 accepts the invitation:
```
C2 -> S: GAME_INVITE_RESP {"status": "ACCEPT", "sender": "<C1_username>"}
```
If Client 2 rejects the invitation:
```
C2 -> S: GAME_INVITE_RESP {"status": "REJECT", "sender": "<C1_username>"}
```
### 9.2.2. Happy flow
If Client 2 does not accept in 10 seconds.
```
S -> C GAME_START_RESP {"status": "ERROR", "code": <error code>, "player1": "<C1_username>", "player2": "<C2_username>"}
```

| Error code | Description                                              |
|------------|----------------------------------------------------------|
| 11004      | Player `<C2_username>` didn't accept the invite in time. | 