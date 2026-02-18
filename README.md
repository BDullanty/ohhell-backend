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

## Remote Start/Stop (from Windows PowerShell)
Use these from your local machine to control the server process on EC2.

Set variables once:
```powershell
$KeyPath = "C:\Users\bdull\Documents\OH HELL\EC2 Keypair.pem"
$Remote = "ec2-user@204.236.160.128"
$RemoteJar = "/opt/ohhell/ohhell-server-java17.jar"
$RemoteClasspath = "$RemoteJar:/opt/ohhell/lib/json-20240303.jar"
```

Upload latest local build and overwrite fixed remote jar path:
```powershell
scp -i "$KeyPath" "$((Get-ChildItem 'C:\Users\bdull\RubymineProjects\ohhell-backend\ec2 HTTP server\ohhell-server-java17-v*.jar' | Sort-Object LastWriteTime | Select-Object -Last 1).FullName)" "$Remote:$RemoteJar"
```

Start (or restart) server:
```powershell
ssh -i "$KeyPath" $Remote "mkdir -p /opt/ohhell/logs; pkill -f 'HTTPHandlers.HTTPServer' || true; nohup java -cp '$RemoteClasspath' HTTPHandlers.HTTPServer > /opt/ohhell/logs/server.log 2>&1 < /dev/null & echo \$! > /opt/ohhell/ohhell.pid"
```

Stop server:
```powershell
ssh -i "$KeyPath" $Remote "if [ -f /opt/ohhell/ohhell.pid ]; then kill \$(cat /opt/ohhell/ohhell.pid) && rm -f /opt/ohhell/ohhell.pid; else pkill -f 'HTTPHandlers.HTTPServer' || true; fi"
```

Check status:
```powershell
ssh -i "$KeyPath" $Remote "if [ -f /opt/ohhell/ohhell.pid ] && ps -p \$(cat /opt/ohhell/ohhell.pid) > /dev/null; then echo RUNNING pid=\$(cat /opt/ohhell/ohhell.pid); else pgrep -af 'HTTPHandlers.HTTPServer' || echo STOPPED; fi"
```

Tail logs:
```powershell
ssh -i "$KeyPath" $Remote "tail -n 120 /opt/ohhell/logs/server.log"
```

Notes:
- Update `$RemoteJar` when you deploy a new jar version.
- `pkill` in the start command ensures only one game server process is running.

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
- `Bet` -> `http://<ec2-ip>:8080/Bet`
- `PlayCard` -> `http://<ec2-ip>:8080/PlayCard`
- `ListPlayers` -> `http://<ec2-ip>:8080/ListPlayers` (optional)

Use the request/response templates in `*.txt`, including:
- `$Connect Request Template.txt`, `$Connect Integration Response.txt`
- `createGame Request Template.txt`, `createGame Integration Response.txt`
- `Bet Request Template.txt` for `Bet`
- `PlayCard Request Template.txt` for `PlayCard`
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
