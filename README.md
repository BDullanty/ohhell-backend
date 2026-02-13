# OH Hell Backend (EC2 HTTP Server)

## Overview
This backend is a Java HTTP server that handles WebSocket route integrations from API Gateway, keeps in-memory lobby/game state, and uses the API Gateway Management API to push updates to connected clients.

## Directory layout
- `ec2 HTTP server/`: Java sources, `ohhell-server.jar`, `lib/json-20240303.jar`, `sources.txt`
- `models/`: shared models and notes
- `*.txt`: API Gateway request/response templates for each route

## Prerequisites
- Java 17+ (JDK for build, JRE for run)
- AWS account with a WebSocket API Gateway
- EC2 instance reachable by API Gateway on port 8080
- IAM role with `execute-api:ManageConnections`

## Configure API Gateway host and region
Edit `ec2 HTTP server/HTTPHandlers/AWSSigner.java`:
- `RESTAPIHOST`: `<api-id>.execute-api.<region>.amazonaws.com`
- `STAGE`: e.g. `production`
- `REGION`: e.g. `us-west-1`

Note: `AWSSigner` uses the EC2 Instance Metadata Service (IMDS) to fetch credentials. It must run on EC2 with an attached IAM role. For local development, you will need to modify `AWSSigner` to use static or env credentials, or stub out ManageConnections.

## Build
From `ec2 HTTP server`:

PowerShell (Windows):
```powershell
New-Item -ItemType Directory -Force build\classes | Out-Null
javac -encoding UTF-8 -cp lib\json-20240303.jar -d build\classes @sources.txt
jar --create --file ohhell-server.jar --main-class HTTPHandlers.HTTPServer -C build\classes .
```

## Run
The server listens on port 8080.

Windows:
```powershell
java -cp "ohhell-server.jar;lib\json-20240303.jar" HTTPHandlers.HTTPServer
```

Linux/macOS:
```bash
java -cp "ohhell-server.jar:lib/json-20240303.jar" HTTPHandlers.HTTPServer
```

## Deploy to EC2 (quick path)
1. Launch an EC2 instance (Amazon Linux 2/2023).
2. Attach an IAM role with `execute-api:ManageConnections`.
3. Open security group inbound for TCP 8080 (from API Gateway IPs or your VPC).
4. Upload the jar and dependency:
   - `scp -i <key.pem> ohhell-server.jar ec2-user@<ip>:/opt/ohhell/`
   - `scp -i <key.pem> -r lib ec2-user@<ip>:/opt/ohhell/`
5. Start the server:
   - `java -cp "/opt/ohhell/ohhell-server.jar:/opt/ohhell/lib/json-20240303.jar" HTTPHandlers.HTTPServer`

Optional systemd service:
```ini
[Unit]
Description=OH Hell HTTP Server
After=network.target

[Service]
User=ec2-user
WorkingDirectory=/opt/ohhell
ExecStart=/usr/bin/java -cp /opt/ohhell/ohhell-server.jar:/opt/ohhell/lib/json-20240303.jar HTTPHandlers.HTTPServer
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

## API Gateway WebSocket configuration
Route selection expression:
```
$request.body.action
```

Routes and HTTP integrations (port 8080):
- `$connect` -> `http://<ec2-ip>:8080/Connect`
- `$disconnect` -> `http://<ec2-ip>:8080/Disconnect`
- `createGame` -> `http://<ec2-ip>:8080/CreateGame`
- `JoinGame` -> `http://<ec2-ip>:8080/JoinGame`
- `LeaveGame` -> `http://<ec2-ip>:8080/LeaveGame`
- `VoteStart` -> `http://<ec2-ip>:8080/VoteStart`
- `PlayCard` -> `http://<ec2-ip>:8080/PlayCard`
- `ListPlayers` -> `http://<ec2-ip>:8080/ListPlayers` (optional)

Use the request/response templates in `*.txt`, including:
- `$Connect Request Template.txt`, `$Connect Integration Response.txt`
- `createGame Request Template.txt`, `createGame Integration Response.txt`
- `ListPlayers Request Template.txt`, `ListPlayers Integration Response.txt`
- `Basic Route Response Template.txt` for generic responses

The `$connect` template expects the access token in the `Authorization` query param (the client connects with `?Authorization=<token>`).

## IAM role policy example
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "execute-api:ManageConnections",
      "Resource": "arn:aws:execute-api:us-west-1:<ACCOUNT_ID>:<API_ID>/production/*/@connections/*"
    }
  ]
}
```

## Troubleshooting
- 403 on ManageConnections: role missing `execute-api:ManageConnections` or wrong API ID/stage/region.
- No lobby updates: `RESTAPIHOST` or `STAGE` mismatch.
- Missing usernames: check `$connect` request template and Authorization query param.
- State resets on restart: game/lobby state is in-memory only.
