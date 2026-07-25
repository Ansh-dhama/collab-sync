# Real-Time Collaborative Document Sync Engine

A backend-first Google Docs-style collaborative editing engine built using Java, Spring Boot, MongoDB, and WebSocket.

This project allows multiple users to edit the same document in real time. Instead of replacing the whole document content, every change is stored as an operation such as INSERT, DELETE, REVERT, or NO_OP. The system maintains document versions, handles concurrent edits, supports history replay, revert, offline sync, reconnect handling, and live user presence.

---

## Project Goal

The goal of this project is to understand and implement the core backend concepts behind real-time collaborative editors like Google Docs.

The system focuses on:

- Real-time editing using WebSocket
- Operation-based document updates
- Version control using baseVersion and serverVersion
- Conflict resolution using Operational Transformation
- Document history and replay
- Revert to old document version
- Offline pending operation sync
- Duplicate operation handling
- Reconnect-safe WebSocket sessions
- User presence updates like typing and cursor position

---

## Tech Stack

- Java 17
- Spring Boot
- Spring WebSocket
- Spring Data MongoDB
- MongoDB Atlas
- Maven
- HTML, CSS, JavaScript for basic frontend demo

---

## High Level Architecture

```text
Browser / Client
   |
   | REST API
   | WebSocket
   |
Spring Boot Backend
   |
   |-- Document Controller
   |-- Operation Controller
   |-- Sync Controller
   |-- Revert Controller
   |-- WebSocket Handler
   |
Service Layer
   |
   |-- DocumentService
   |-- OperationService
   |-- DocumentStateService
   |-- DocumentHistoryService
   |-- OperationalTransformationEngine
   |-- DocumentSessionManager
   |
MongoDB
   |
   |-- documents
   |-- document_operations