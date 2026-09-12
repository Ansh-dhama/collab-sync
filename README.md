Real-Time Collaborative Document Sync Engine

🚀 Live Demo: https://collab-sync-68rm.onrender.com/index.html

A Google Docs-style real-time collaborative document editing engine built with Java, Spring Boot, MongoDB Atlas, WebSocket, JWT Authentication, Operational Transformation, and React.

This project allows multiple authenticated users to edit the same document in real time. It uses operation-based synchronization instead of replacing the full document content, making concurrent editing safer and more reliable.

📌 Project Overview

In a normal editor, updating the full document content can overwrite another user's changes.

This project solves that problem using an operation-based editing model.

Instead of sending the complete document again and again, the frontend sends operations like:

{
  "operationId": "op-a-1",
  "clientId": "user-a",
  "baseVersion": 0,
  "type": "INSERT",
  "position": 5,
  "text": " from A"
}

The backend checks the document version, transforms stale operations when needed, applies the final operation safely, stores history, and broadcasts updates to connected users.

✨ Features

Authentication

User registration

User login

JWT token generation

BCrypt password hashing

Protected REST APIs

WebSocket authentication using JWT token

Document Management

Create document

Get document by ID

List documents

Store initial content and current content

Track document version

Track created and updated timestamps

Real-Time Collaboration

WebSocket-based live editing

Multiple users can edit the same document

Active collaborator tracking

Same-account multi-tab support

Reconnect-safe WebSocket sessions

Live document sync after reconnect

Operation-Based Editing

INSERT operation

DELETE operation

REVERT operation

Internal NO_OP support

baseVersion and serverVersion tracking

Duplicate operation handling

Idempotent operation processing

Conflict Resolution

Operational Transformation for stale operations

Concurrent insert handling

Insert/delete conflict handling

Delete/delete overlap handling

Document-level locking for safe updates

Prevents stale clients from overwriting latest server content

Offline Sync

Offline draft support

Pending operation sync after reconnect

Offline changes merge with latest online changes

Online user changes are preserved

Conflict-safe reconnect flow

History and Recovery

Full operation history

Replay document content at any version

Revert document to a previous version

Version-based document reconstruction

Frontend

Professional React frontend

Login and register screens

Error notifications

Document editor UI

Recent documents list

Active collaborators panel

Offline/reconnect status

Frontend built into Spring Boot static folder for deployment

🛠️ Tech Stack

Layer

Technology

Backend

Java 17, Spring Boot

Frontend

React, Vite, JavaScript

Database

MongoDB Atlas

Real-time Communication

WebSocket

Security

Spring Security, JWT, BCrypt

Build Tool

Maven

Deployment

Render

Architecture

REST API + WebSocket + Operation-based Sync

🧠 Core Architecture

React Frontend
     |
     | REST APIs
     | WebSocket
     |
Spring Boot Backend
     |
     |-- AuthController
     |-- DocumentController
     |-- OperationController
     |-- DocumentSyncController
     |-- DocumentRevertController
     |-- DocumentWebSocketHandler
     |
Service Layer
     |
     |-- AuthService
     |-- JwtService
     |-- OperationService
     |-- DocumentStateService
     |-- OperationalTransformationEngine
     |-- DocumentSessionManager
     |-- DocumentLockManager
     |
MongoDB Atlas
     |
     |-- app_users
     |-- documents
     |-- document_operations

🔐 Environment Variables

Create these environment variables before running or deploying:

MONGODB_URI=mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/collab_sync_engine?appName=colabSync
JWT_SECRET=your-long-secret-key-minimum-32-characters

application.properties should not contain real secrets:

server.port=${PORT:8081}

spring.mongodb.uri=${MONGODB_URI}

jwt.secret=${JWT_SECRET}
jwt.expiration-ms=86400000

▶️ Run Backend Locally

export MONGODB_URI='mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/collab_sync_engine?appName=colabSync'
export JWT_SECRET='your-long-secret-key-minimum-32-characters'

mvn clean spring-boot:run

Backend will run on:

http://localhost:8081

⚛️ Run React Frontend Locally

Go to frontend folder:

cd frontend
npm install
npm run dev

📦 Build React Frontend into Spring Boot

Build React and copy it into Spring Boot static resources:

SPRING_DIR="/path/to/your/spring-project" npm run install:spring

After build, Spring Boot serves the frontend from:

http://localhost:8081/index.html

🔗 API Endpoints

Auth APIs

Register

POST /api/auth/register

Request:

{
  "name": "User A",
  "email": "usera@test.com",
  "password": "123456"
}

Login

POST /api/auth/login

Request:

{
  "email": "usera@test.com",
  "password": "123456"
}

Document APIs

Create Document

POST /api/documents
Authorization: Bearer <JWT_TOKEN>

Request:

{
  "title": "Render API Test",
  "initialContent": "Hello",
  "createdBy": "User A"
}

Get Document

GET /api/documents/{documentId}
Authorization: Bearer <JWT_TOKEN>

List Documents

GET /api/documents
Authorization: Bearer <JWT_TOKEN>

Operation APIs

Apply Operation

POST /api/documents/{documentId}/operations
Authorization: Bearer <JWT_TOKEN>

Request:

{
  "operationId": "op-a-1",
  "clientId": "user-a",
  "baseVersion": 0,
  "type": "INSERT",
  "position": 5,
  "text": " from A",
  "length": null
}

History APIs

Get Operation History

GET /api/documents/{documentId}/history
Authorization: Bearer <JWT_TOKEN>

Replay Version

GET /api/documents/{documentId}/versions/{version}
Authorization: Bearer <JWT_TOKEN>

Offline Sync API

POST /api/documents/{documentId}/sync
Authorization: Bearer <JWT_TOKEN>

Request:

{
  "clientId": "user-b",
  "lastKnownServerVersion": 0,
  "pendingOperations": [
    {
      "operationId": "offline-b-1",
      "baseVersion": 0,
      "type": "INSERT",
      "position": 5,
      "text": " offline-B",
      "length": null
    }
  ]
}

Revert API

POST /api/documents/{documentId}/revert
Authorization: Bearer <JWT_TOKEN>

🔌 WebSocket Endpoint

/ws/documents/{documentId}?clientId={clientId}&lastKnownVersion={version}&token={JWT_TOKEN}

Local example:

ws://localhost:8081/ws/documents/{documentId}?clientId=user-a&lastKnownVersion=0&token=JWT_TOKEN

Production example:

wss://collab-sync-68rm.onrender.com/ws/documents/{documentId}?clientId=user-a&lastKnownVersion=0&token=JWT_TOKEN

🧪 Tested Deployment Result

The deployed Render version was tested with:

Frontend home page

Register API

Login API

Create document API

Get document API

List documents API

Apply operation API

Stale operation conflict handling

History API

Version replay API

Offline sync API

Final document verification

Final tested merge result:

Hello from A from B offline-B

This confirms that online and offline changes are preserved during reconnect.

🚀 Deploy on Render

Render settings:

Runtime: Docker
Branch: main
Root Directory: blank
Dockerfile Path: ./Dockerfile
Region: Singapore

Environment variables:

MONGODB_URI=mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/collab_sync_engine?appName=colabSync
JWT_SECRET=your-long-secret-key-minimum-32-characters

After deploy:

https://collab-sync-68rm.onrender.com/index.html

📊 Project Rating

Category

Rating

Backend Architecture

8.5/10

Real-Time Collaboration

8/10

Operation Sync

9/10

Offline Conflict Handling

9/10

Authentication

8/10

Frontend UI

8/10

Render MVP Deployment

9/10

Overall Portfolio Value

9/10

⚠️ Current Limitations

This is a strong portfolio/MVP project, but it is not a full Google Docs replacement yet.

Future improvements:

Document permissions and sharing

User-specific document listing

Refresh token support

Redis Pub/Sub for multi-instance WebSocket scaling

Distributed locking

Snapshot optimization

Rich text editor support

More advanced OT/CRDT implementation

Full integration and load testing

🧾 Resume Summary

Built a Google Docs-style real-time collaborative document editing engine using Java, Spring Boot, MongoDB Atlas, WebSocket, JWT, and React. Implemented operation-based editing, version tracking, Operational Transformation, live collaboration, offline sync, duplicate operation handling, history replay, revert support, reconnect-safe WebSocket sessions, and conflict-safe offline merge.
