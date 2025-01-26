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
S -> C2: GAME_INVITE {"sender": "<C1_username>"}
```

### 9.1.2. Unhappy flow
```
S -> C1: GAME_PREPARE_RESP {"status": "ERROR", "code": <error code>}
```
Possible `<error code>`:

| Error code | Description                                                   |
|------------|---------------------------------------------------------------|
| 11001      | No receiver found                                             | 
| 11002      | Can't send game request to self                               | 

In case there's an ongoing game between other players (referred as `C3` and `C4`):
```
S -> C1: GAME_PREPARE_RESP {"status": "ERROR", "code": <error code>}
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
```
S -> C1: GAME_PREPARE_RESP {"status": "ERROR", "code": <error code>}
```
Possible `<error code>`:

| Error code | Description                             |
|------------|-----------------------------------------|
| 11004      | Game invite was rejected                |

### 9.2.2 Unhappy flow
none.
## 9.3 Game Starts
When the second users accept the game all users receive:
### 9.3.1 Happy flow
```
S -> C: GAME_START_RESP {"playerOne": "<C1_username>","playerTwo": "<C2_username>"}
```
### 9.3.2 Unhappy flow
none.
## 9.4 Picking an action
When the user wants to pick an action(Rock,Paper,Scissors):
### 9.4.1 Happy flow
```
C1 -> S: ACTION_REQUEST {"action": "<action>"}
```
### 9.4.2 Unhappy flow
if the user makes a forbidden action he is going to receive:
```
S -> C1: GAME_PREPARE_RESP {"status": "ERROR", "code": <error code>}
```
Possible `<error code>`:

| Error code | Description                                 |
|------------|---------------------------------------------|
| 12001      | Making action without being in a game.      |
| 12002      | Making second action in one game.           |
| 12003      | Action different from Rock, Paper, Scissors |


## 9.5 Winner
When both players have made an action:
### 9.5.1 Happy flow
If player One wins Server will send to all users:
```
S -> C: GAME_WINNER {"winner": "<player one username>"}
```
### 9.5.2 unHappy flow
none.

# 10. File Transmit

This section explains the process of confirming or rejecting a file transfer
request and the steps involved in transmitting a file between two parties (uploader and downloader).
## 10.1 File Transfer Request
The uploader initiates a file transfer request to the server.
### 10.1.1 Uploader Request
```
Uploader -> S: FILE_UPLOAD_REQ {"to": "<downloader>", "hash": "<original_file>", "fileExtension": <"file_extension">}
S -> Uploader: FILE_UPLOAD_RESP {"status": "OK"}
```
Server notifies downloader of the file transfer request.
### 10.1.2 Server Notification to Downloader
```
S -> Downloader: FILE_DOWNLOAD_REQ {"from": "<uploader>", "hash": "<original_file>", "fileExtension": "<file_extension>"}
```
### 10.1.3 Downloader Response
The downloader confirms or rejects the file transfer request.
``` 
Downloader -> S: FILE_DOWNLOAD_RESP {"from": "<downloader>", "status": "OK/ERROR"}
```
### 10.1.4 Server Updates Both Parties
The server notifies both the uploader and downloader about the status of the request.
``` 
S -> Uploader: FILE_DOWNLOAD_STATUS_RESP {"status": "OK/ERROR"}
S -> Downloader: FILE_DOWNLOAD_STATUS_RESP {"status": "OK/ERROR"}
```
## 10.2 Generating a UUID for the File Transfer
The server assigns a unique identifier (UUID) to identify 
the uploader and downloader during the file transmission process.
### 10.2.1 UUID Distribution
The server sends the UUIDs to both parties, with suffixes indicating their roles:
`s: Uploader (sender)`
`r: Downloader (receiver)`

``` 
S -> Uploader: FILE_SEND_UUID {"uuid": "<uuid>s"}
S -> Downloader: FILE_SEND_UUID {"uuid": "<uuid>r"}
```
## 10.3 Establishing a Transfer Socket
Both clients establish separate connections on port 1338 for file transmission.
The server uses the UUID suffix to determine the client’s role.
### 10.3.1 Uploader Starts Transmission
The uploader begins sending the file data if the UUID ends with "s".
``` 
Uploader -> S: (transfer socket) <uuid>s<FILE_DATA>
```
### 10.3.2 Downloader Receives Data
The downloader listens for and receives the transmitted file data if the UUID ends with "r".
``` 
Downloader -> S: (transfer socket) <uuid>r
S -> Downloader: (transfer socket) <RECEIVE_DATA>
```
## 10.4 Definitions
`FILE_DATA: A small chunk of file data being sent by the uploader.`
`RECEIVE_DATA: A small chunk of file data being received by the downloader.`

