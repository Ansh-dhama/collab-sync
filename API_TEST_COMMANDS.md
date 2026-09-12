# Collab Sync Engine API Test Commands

Set env variables locally before starting backend:

```bash
export MONGODB_URI='mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/collab_sync_engine?appName=colabSync'
export JWT_SECRET='a-long-secret-key-minimum-32-characters-for-jwt'
mvn clean spring-boot:run
```

Base URL:

```bash
BASE='http://localhost:8081'
```

## 1. Register User A

```bash
curl -s -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"name":"User A","email":"usera@test.com","password":"123456"}'
```

## 2. Login User A

```bash
TOKEN_A=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"usera@test.com","password":"123456"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "$TOKEN_A"
```

## 3. Register/Login User B

```bash
curl -s -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"name":"User B","email":"userb@test.com","password":"123456"}'

TOKEN_B=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"userb@test.com","password":"123456"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "$TOKEN_B"
```

## 4. Create document

```bash
DOC_ID=$(curl -s -X POST "$BASE/api/documents" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{"title":"Offline Conflict Test","initialContent":"Hello","createdBy":"usera@test.com"}' | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('documentId') or d.get('id'))")

echo "$DOC_ID"
```

## 5. Online operation from User A

```bash
curl -s -X POST "$BASE/api/documents/$DOC_ID/operations" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{
    "operationId":"user-a-op-1",
    "clientId":"user-a",
    "baseVersion":0,
    "type":"INSERT",
    "position":5,
    "text":" A-online",
    "length":null
  }'
```

Expected content after this: `Hello A-online`

## 6. Offline insert sync from User B with old baseVersion 0

```bash
curl -s -X POST "$BASE/api/documents/$DOC_ID/sync" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN_B" \
  -d '{
    "clientId":"user-b",
    "lastKnownServerVersion":0,
    "pendingOperations":[
      {
        "operationId":"user-b-offline-op-1",
        "baseVersion":0,
        "type":"INSERT",
        "position":5,
        "text":" B-offline",
        "length":null
      }
    ]
  }'
```

Expected final content: `Hello A-online B-offline`

## 7. Load document and verify

```bash
curl -s "$BASE/api/documents/$DOC_ID" \
  -H "Authorization: Bearer $TOKEN_A"
```

## 8. History

```bash
curl -s "$BASE/api/documents/$DOC_ID/history" \
  -H "Authorization: Bearer $TOKEN_A"
```

## 9. Replay version

```bash
curl -s "$BASE/api/documents/$DOC_ID/versions/1" \
  -H "Authorization: Bearer $TOKEN_A"
```

## 10. Revert

```bash
curl -s -X POST "$BASE/api/documents/$DOC_ID/revert" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{
    "operationId":"revert-op-1",
    "clientId":"user-a",
    "baseVersion":2,
    "targetVersion":1
  }'
```
