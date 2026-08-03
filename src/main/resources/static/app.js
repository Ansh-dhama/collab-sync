(() => {
    "use strict";

    const API_BASE = window.location.origin;
    const WS_SCHEME = window.location.protocol === "https:" ? "wss:" : "ws:";
    const WS_BASE = `${WS_SCHEME}//${window.location.host}`;

    const state = {
        socket: null,
        documentId: "",
        clientId: "",
        currentVersion: 0,
        acknowledgedContent: "",
        previousEditorContent: "",
        operationInFlight: null,
        pendingOffline: [],
        applyingRemoteChange: false,
        manuallyDisconnected: false,
        reconnectAttempts: 0,
        reconnectTimer: null,
        inputFlushTimer: null,
        presenceTimer: null,
        typingStopTimer: null,
        presenceByClient: new Map(),
        dialogResolver: null
    };

    const dom = {};

    document.addEventListener("DOMContentLoaded", init);

    function init() {
        cacheDom();
        initializeClientId();
        restoreLastDocument();
        bindEvents();
        updateEditorMetrics();
        updateConnectionUi("offline");
        updateSaveState("ready", "Ready");
        updateHealth("idle", "Document not loaded");
        autoResizeEditor();
        log("Frontend initialized");
    }

    function cacheDom() {
        [
            "documentTitle",
            "documentId",
            "clientId",
            "connectButton",
            "disconnectButton",
            "createButton",
            "loadButton",
            "copyDocumentIdButton",
            "connectionBadge",
            "connectionStatus",
            "saveState",
            "saveStateIcon",
            "sessionVersionBadge",
            "wordCount",
            "characterCount",
            "pendingCount",
            "currentVersion",
            "activeClientCount",
            "activeClients",
            "presenceList",
            "logs",
            "fontSelect",
            "fontSizeSelect",
            "decreaseIndentButton",
            "increaseIndentButton",
            "healthIndicator",
            "healthText",
            "editor",
            "cursorPosition",
            "selectionStatus",
            "toastRegion",
            "confirmDialog",
            "dialogMessage",
            "dialogCancel",
            "dialogConfirm"
        ].forEach((id) => {
            dom[id] = document.getElementById(id);
        });
    }

    function bindEvents() {
        dom.createButton.addEventListener("click", createDocument);
        dom.loadButton.addEventListener("click", loadDocument);
        dom.connectButton.addEventListener("click", connectWebSocket);
        dom.disconnectButton.addEventListener("click", () => disconnectWebSocket(true));
        dom.copyDocumentIdButton.addEventListener("click", copyDocumentId);

        dom.editor.addEventListener("input", handleEditorInput);
        dom.editor.addEventListener("keyup", handleCursorActivity);
        dom.editor.addEventListener("click", handleCursorActivity);
        dom.editor.addEventListener("select", handleCursorActivity);
        dom.editor.addEventListener("blur", () => sendPresence(false));

        dom.fontSelect.addEventListener("change", () => {
            dom.editor.style.fontFamily = dom.fontSelect.value;
        });

        dom.fontSizeSelect.addEventListener("change", () => {
            dom.editor.style.fontSize = `${dom.fontSizeSelect.value}px`;
            autoResizeEditor();
        });

        dom.increaseIndentButton.addEventListener("click", () => indentSelection("    "));
        dom.decreaseIndentButton.addEventListener("click", decreaseIndent);

        dom.documentId.addEventListener("change", () => {
            state.documentId = dom.documentId.value.trim();
            loadPendingOffline();
        });

        dom.clientId.addEventListener("change", () => {
            state.clientId = dom.clientId.value.trim();
            sessionStorage.setItem("collab.clientId", state.clientId);
            loadPendingOffline();
        });

        dom.dialogCancel.addEventListener("click", () => closeDialog(false));
        dom.dialogConfirm.addEventListener("click", () => closeDialog(true));

        window.addEventListener("online", handleBrowserOnline);
        window.addEventListener("offline", handleBrowserOffline);
        window.addEventListener("beforeunload", handleBeforeUnload);
    }

    function initializeClientId() {
        let clientId = sessionStorage.getItem("collab.clientId");

        if (!clientId) {
            const randomPart = crypto?.randomUUID
                ? crypto.randomUUID().slice(0, 8)
                : Math.random().toString(36).slice(2, 10);

            clientId = `user-${randomPart}`;
            sessionStorage.setItem("collab.clientId", clientId);
        }

        state.clientId = clientId;
        dom.clientId.value = clientId;
    }

    function restoreLastDocument() {
        const documentId = localStorage.getItem("collab.lastDocumentId") || "";
        state.documentId = documentId;
        dom.documentId.value = documentId;
        loadPendingOffline();

        if (documentId) {
            const draft = localStorage.getItem(draftKey());

            if (draft !== null && state.pendingOffline.length > 0) {
                setEditorContent(draft, { remote: false });
                updateHealth("warning", "Offline draft restored");
                showToast("Restored unsynced local changes.", "warning");
            }
        }
    }

    async function createDocument() {
        const title = dom.documentTitle.value.trim() || "Untitled document";
        const clientId = getClientId();

        if (!clientId) {
            showToast("Client ID is required.", "error");
            dom.clientId.focus();
            return;
        }

        setBusy(dom.createButton, true, "Creating…");

        try {
            const response = await fetchJson(`${API_BASE}/api/documents`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    title,
                    initialContent: "",
                    createdBy: clientId
                })
            });

            const documentId = response.documentId || response.id;

            if (!documentId) {
                throw new Error("The server did not return a document ID.");
            }

            state.documentId = documentId;
            dom.documentId.value = documentId;
            localStorage.setItem("collab.lastDocumentId", documentId);

            state.currentVersion = Number(response.currentVersion || 0);
            state.acknowledgedContent = response.currentContent || "";
            state.previousEditorContent = state.acknowledgedContent;
            state.pendingOffline = [];
            persistPendingOffline();
            setEditorContent(state.acknowledgedContent, { remote: true });

            updateVersionUi();
            updateHealth("ready", "Document ready");
            updateSaveState("saved", "Created");
            showToast("Document created successfully.", "success");
            log(`Document created: ${documentId}`);
        } catch (error) {
            updateSaveState("error", "Create failed");
            showToast(error.message, "error");
            log(`Create failed: ${error.message}`);
        } finally {
            setBusy(dom.createButton, false, "New document");
        }
    }

    async function loadDocument() {
        const documentId = getDocumentId();

        if (!documentId) {
            showToast("Enter a document ID first.", "error");
            dom.documentId.focus();
            return;
        }

        if (hasUnsyncedChanges()) {
            const proceed = await confirmAction(
                "You have unsynced changes. Loading another server copy can replace them."
            );

            if (!proceed) {
                return;
            }
        }

        setBusy(dom.loadButton, true, "Loading…");

        try {
            const response = await fetchJson(`${API_BASE}/api/documents/${encodeURIComponent(documentId)}`);

            state.documentId = documentId;
            state.currentVersion = Number(response.currentVersion || 0);
            state.acknowledgedContent = response.currentContent || "";
            state.previousEditorContent = state.acknowledgedContent;
            state.operationInFlight = null;

            dom.documentTitle.value = response.title || "Untitled document";
            localStorage.setItem("collab.lastDocumentId", documentId);

            loadPendingOffline();

            if (state.pendingOffline.length > 0) {
                const draft = localStorage.getItem(draftKey());
                setEditorContent(draft ?? state.acknowledgedContent, { remote: false });
                updateHealth("warning", "Offline edits waiting to sync");
                updateSaveState("syncing", "Pending sync");
            } else {
                setEditorContent(state.acknowledgedContent, { remote: true });
                updateHealth("ready", "Document loaded");
                updateSaveState("saved", "Loaded");
            }

            updateVersionUi();
            showToast("Document loaded.", "success");
            log(`Loaded document at version ${state.currentVersion}`);
        } catch (error) {
            updateHealth("error", "Load failed");
            showToast(error.message, "error");
            log(`Load failed: ${error.message}`);
        } finally {
            setBusy(dom.loadButton, false, "Load");
        }
    }

    function connectWebSocket() {
        const documentId = getDocumentId();
        const clientId = getClientId();

        if (!documentId || !clientId) {
            showToast("Document ID and Client ID are required.", "error");
            return;
        }

        if (state.socket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(state.socket.readyState)) {
            showToast("A WebSocket session is already active.", "warning");
            return;
        }

        state.documentId = documentId;
        state.clientId = clientId;
        state.manuallyDisconnected = false;
        localStorage.setItem("collab.lastDocumentId", documentId);
        sessionStorage.setItem("collab.clientId", clientId);
        loadPendingOffline();

        const url = `${WS_BASE}/ws/documents/${encodeURIComponent(documentId)}`
            + `?clientId=${encodeURIComponent(clientId)}`
            + `&lastKnownVersion=${state.currentVersion}`;

        updateConnectionUi("connecting");
        log("Connecting WebSocket…");

        const socket = new WebSocket(url);
        state.socket = socket;

        socket.addEventListener("open", () => {
            if (state.socket !== socket) return;

            state.reconnectAttempts = 0;
            updateConnectionUi("online");
            updateHealth("ready", "Live collaboration active");
            showToast("Connected to the document.", "success");
            log("WebSocket connected");
        });

        socket.addEventListener("message", (event) => {
            if (state.socket !== socket) return;

            try {
                const message = JSON.parse(event.data);
                handleServerMessage(message);
            } catch (error) {
                log(`Invalid server message: ${error.message}`);
            }
        });

        socket.addEventListener("close", () => {
            if (state.socket === socket) {
                state.socket = null;
            }

            moveInFlightOperationToOfflineQueue();
            updateConnectionUi("offline");

            if (!state.manuallyDisconnected && navigator.onLine) {
                scheduleReconnect();
            } else {
                updateHealth("warning", "Working offline");
            }

            log("WebSocket disconnected");
        });

        socket.addEventListener("error", () => {
            updateHealth("error", "Connection problem");
            log("WebSocket transport error");
        });
    }

    function disconnectWebSocket(manual = true) {
        state.manuallyDisconnected = manual;
        clearTimeout(state.reconnectTimer);

        if (state.socket) {
            state.socket.close(1000, "Client disconnected");
            state.socket = null;
        } else {
            updateConnectionUi("offline");
        }
    }

    function scheduleReconnect() {
        clearTimeout(state.reconnectTimer);

        state.reconnectAttempts += 1;
        const delay = Math.min(1000 * (2 ** (state.reconnectAttempts - 1)), 15000);

        updateConnectionUi("connecting", `Reconnecting in ${Math.ceil(delay / 1000)}s`);
        log(`Reconnect scheduled in ${delay}ms`);

        state.reconnectTimer = setTimeout(() => {
            if (!state.manuallyDisconnected && navigator.onLine) {
                connectWebSocket();
            }
        }, delay);
    }

    function handleServerMessage(message) {
        const type = String(message.messageType || "").toUpperCase();
        log(`Received ${type || "UNKNOWN"}`);

        switch (type) {
            case "CONNECTED":
                updateActiveClients(message.activeClients);
                break;

            case "DOCUMENT_SYNC":
                handleDocumentSync(message);
                break;

            case "OPERATION_APPLIED":
                handleOperationApplied(message);
                break;

            case "CLIENT_JOINED":
            case "CLIENT_LEFT":
                updateActiveClients(message.activeClients);
                if (message.clientId) {
                    showToast(
                        `${message.clientId} ${type === "CLIENT_JOINED" ? "joined" : "left"} the document.`,
                        "success"
                    );
                }
                break;

            case "PRESENCE_UPDATE":
                updatePresence(message);
                break;

            case "ERROR":
                handleServerError(message);
                break;

            default:
                log(`Ignored unsupported message type: ${type}`);
        }
    }

    function handleDocumentSync(message) {
        const serverContent = message.currentContent || "";
        state.currentVersion = Number(message.currentVersion ?? message.serverVersion ?? 0);
        state.acknowledgedContent = serverContent;
        updateVersionUi();

        if (state.pendingOffline.length > 0) {
            updateSaveState("syncing", `Syncing ${state.pendingOffline.length} offline change(s)…`);
            syncOfflineOperations();
            return;
        }

        setEditorContent(serverContent, { remote: true });
        updateSaveState("saved", "Up to date");
        updateHealth("ready", "Live collaboration active");
    }

    function handleOperationApplied(message) {
        const previousAcknowledged = state.acknowledgedContent;
        const serverContent = message.currentContent || "";
        const isOwnAck = Boolean(
            state.operationInFlight
            && message.operationId === state.operationInFlight.operationId
        );
        const hadLocalChanges = dom.editor.value !== previousAcknowledged || Boolean(state.operationInFlight);

        state.currentVersion = Number(message.currentVersion ?? message.serverVersion ?? state.currentVersion);
        state.acknowledgedContent = serverContent;
        updateVersionUi();
        updateActiveClients(message.activeClients);

        if (isOwnAck) {
            state.operationInFlight = null;
        }

        if (!hadLocalChanges || dom.editor.value === previousAcknowledged) {
            setEditorContent(serverContent, { remote: true });
        }

        if (dom.editor.value === state.acknowledgedContent) {
            updateSaveState("saved", "Up to date");
        } else {
            updateSaveState("syncing", "Sending changes…");
        }

        scheduleFlush();
    }

    function handleServerError(message) {
        moveInFlightOperationToOfflineQueue();
        const serverMessage = message.message || "The server rejected the operation.";
        updateSaveState("error", "Sync failed");
        updateHealth("error", "Server rejected a change");
        showToast(serverMessage, "error");
        log(`Server error: ${serverMessage}`);
    }

    function handleEditorInput() {
        if (state.applyingRemoteChange) {
            return;
        }

        autoResizeEditor();
        updateEditorMetrics();
        updateCursorUi();

        const newContent = dom.editor.value;
        const oldContent = state.previousEditorContent;
        state.previousEditorContent = newContent;

        if (!state.documentId) {
            updateSaveState("syncing", "Local draft");
            persistDraft();
            return;
        }

        if (!isSocketOpen()) {
            const operations = calculateOperations(oldContent, newContent);

            for (const operation of operations) {
                queueOfflineOperation(operation);
            }

            persistDraft();
            updateSaveState("syncing", `${state.pendingOffline.length} pending`);
            updateHealth("warning", "Editing offline");
            return;
        }

        updateSaveState("syncing", "Sending changes…");
        persistDraft();
        scheduleFlush();

        clearTimeout(state.typingStopTimer);
        sendPresence(true);
        state.typingStopTimer = setTimeout(() => sendPresence(false), 900);
    }

    function scheduleFlush() {
        clearTimeout(state.inputFlushTimer);
        state.inputFlushTimer = setTimeout(flushDesiredContent, 120);
    }

    function flushDesiredContent() {
        if (!isSocketOpen() || state.operationInFlight) {
            return;
        }

        const desiredContent = dom.editor.value;
        const operations = calculateOperations(state.acknowledgedContent, desiredContent);

        if (operations.length === 0) {
            updateSaveState("saved", "Up to date");
            clearPersistedDraftIfSynced();
            return;
        }

        const firstOperation = operations[0];
        const message = buildOperationMessage(firstOperation, state.currentVersion);

        state.operationInFlight = message;
        state.socket.send(JSON.stringify(message));
        updateSaveState("syncing", "Sending changes…");
        log(`Sent ${message.type} at ${message.position}`);
    }

    function calculateOperations(oldText, newText) {
        if (oldText === newText) {
            return [];
        }

        let start = 0;

        while (
            start < oldText.length
            && start < newText.length
            && oldText[start] === newText[start]
        ) {
            start += 1;
        }

        let oldEnd = oldText.length - 1;
        let newEnd = newText.length - 1;

        while (
            oldEnd >= start
            && newEnd >= start
            && oldText[oldEnd] === newText[newEnd]
        ) {
            oldEnd -= 1;
            newEnd -= 1;
        }

        const deletedLength = Math.max(0, oldEnd - start + 1);
        const insertedText = newText.substring(start, newEnd + 1);
        const operations = [];

        if (deletedLength > 0) {
            operations.push({
                type: "DELETE",
                position: start,
                text: null,
                length: deletedLength
            });
        }

        if (insertedText.length > 0) {
            operations.push({
                type: "INSERT",
                position: start,
                text: insertedText,
                length: null
            });
        }

        return operations;
    }

    function buildOperationMessage(operation, baseVersion, operationId = null) {
        return {
            messageType: "OPERATION",
            operationId: operationId || createOperationId(),
            baseVersion,
            type: operation.type,
            position: operation.position,
            text: operation.text,
            length: operation.length
        };
    }

    function createOperationId() {
        const suffix = crypto?.randomUUID
            ? crypto.randomUUID()
            : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

        return `${getClientId()}-${suffix}`;
    }

    function queueOfflineOperation(operation, operationId = null, baseVersion = null) {
        if (!operation || !["INSERT", "DELETE"].includes(operation.type)) {
            return;
        }

        const queued = {
            operationId: operationId || createOperationId(),
            baseVersion: baseVersion ?? (state.currentVersion + state.pendingOffline.length),
            type: operation.type,
            position: operation.position,
            text: operation.text,
            length: operation.length
        };

        if (!state.pendingOffline.some((item) => item.operationId === queued.operationId)) {
            state.pendingOffline.push(queued);
        }

        persistPendingOffline();
        updatePendingUi();
    }

    function moveInFlightOperationToOfflineQueue() {
        if (!state.operationInFlight) {
            return;
        }

        const operation = state.operationInFlight;

        queueOfflineOperation(
            {
                type: operation.type,
                position: operation.position,
                text: operation.text,
                length: operation.length
            },
            operation.operationId,
            operation.baseVersion
        );

        state.operationInFlight = null;
    }

    async function syncOfflineOperations() {
        if (state.pendingOffline.length === 0 || !state.documentId) {
            return;
        }

        const snapshot = [...state.pendingOffline];

        try {
            const response = await fetchJson(
                `${API_BASE}/api/documents/${encodeURIComponent(state.documentId)}/sync`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        clientId: getClientId(),
                        lastKnownServerVersion: state.currentVersion,
                        pendingOperations: snapshot
                    })
                }
            );

            const results = Array.isArray(response.results) ? response.results : [];
            const failedIndex = results.findIndex((result) => result.status === "FAILED");

            if (failedIndex >= 0) {
                state.pendingOffline = snapshot.slice(failedIndex);
                persistPendingOffline();
                updatePendingUi();

                const failed = results[failedIndex];
                throw new Error(failed.message || "An offline operation could not be synchronized.");
            }

            state.pendingOffline = [];
            persistPendingOffline();
            state.currentVersion = Number(response.latestServerVersion ?? state.currentVersion);
            state.acknowledgedContent = response.currentContent || "";
            setEditorContent(state.acknowledgedContent, { remote: true });
            updateVersionUi();
            updateSaveState("saved", "Offline changes synced");
            updateHealth("ready", "Live collaboration active");
            clearPersistedDraftIfSynced();
            showToast("Offline changes synchronized.", "success");
            log("Offline queue synchronized");
        } catch (error) {
            updateSaveState("error", "Offline sync failed");
            updateHealth("warning", "Offline changes need attention");
            showToast(error.message, "error");
            log(`Offline sync failed: ${error.message}`);
        }
    }

    function loadPendingOffline() {
        try {
            const raw = localStorage.getItem(pendingKey());
            const parsed = raw ? JSON.parse(raw) : [];
            state.pendingOffline = Array.isArray(parsed) ? parsed : [];
        } catch {
            state.pendingOffline = [];
        }

        updatePendingUi();
    }

    function persistPendingOffline() {
        if (!state.documentId || !state.clientId) {
            return;
        }

        localStorage.setItem(pendingKey(), JSON.stringify(state.pendingOffline));
    }

    function persistDraft() {
        if (!state.documentId || !state.clientId) {
            return;
        }

        localStorage.setItem(draftKey(), dom.editor.value);
    }

    function clearPersistedDraftIfSynced() {
        if (dom.editor.value === state.acknowledgedContent && state.pendingOffline.length === 0) {
            localStorage.removeItem(draftKey());
        }
    }

    function pendingKey() {
        return `collab.pending.${state.documentId || "none"}.${state.clientId || "none"}`;
    }

    function draftKey() {
        return `collab.draft.${state.documentId || "none"}.${state.clientId || "none"}`;
    }

    function handleCursorActivity() {
        updateCursorUi();

        clearTimeout(state.presenceTimer);
        state.presenceTimer = setTimeout(() => sendPresence(false), 90);
    }

    function sendPresence(isTyping) {
        if (!isSocketOpen()) {
            return;
        }

        state.socket.send(JSON.stringify({
            messageType: "PRESENCE",
            cursorPosition: dom.editor.selectionStart,
            typing: Boolean(isTyping),
            selectionStart: dom.editor.selectionStart,
            selectionEnd: dom.editor.selectionEnd
        }));
    }

    function updatePresence(message) {
        const clientId = message.clientId;

        if (!clientId || clientId === getClientId()) {
            return;
        }

        state.presenceByClient.set(clientId, {
            typing: Boolean(message.typing),
            cursorPosition: Number(message.cursorPosition || 0),
            selectionStart: message.selectionStart,
            selectionEnd: message.selectionEnd,
            updatedAt: Date.now()
        });

        renderPresence();

        setTimeout(() => {
            const presence = state.presenceByClient.get(clientId);

            if (presence && Date.now() - presence.updatedAt >= 4000) {
                state.presenceByClient.delete(clientId);
                renderPresence();
            }
        }, 4200);
    }

    function renderPresence() {
        dom.presenceList.innerHTML = "";

        if (state.presenceByClient.size === 0) {
            dom.presenceList.innerHTML = '<div class="empty-state">No recent activity</div>';
            return;
        }

        for (const [clientId, presence] of state.presenceByClient.entries()) {
            const item = document.createElement("div");
            item.className = "presence-item";

            const activity = presence.typing
                ? "is typing"
                : `cursor at ${presence.cursorPosition}`;

            item.textContent = `${clientId} ${activity}`;
            dom.presenceList.appendChild(item);
        }
    }

    function updateActiveClients(activeClients) {
        const clients = Array.isArray(activeClients) ? activeClients : [];
        dom.activeClientCount.textContent = String(clients.length);
        dom.activeClients.innerHTML = "";

        if (clients.length === 0) {
            dom.activeClients.innerHTML = '<div class="empty-state">No active collaborators</div>';
            return;
        }

        clients.forEach((clientId, index) => {
            const row = document.createElement("div");
            row.className = "collaborator";

            const avatar = document.createElement("div");
            avatar.className = "avatar";
            avatar.style.background = avatarColor(clientId, index);
            avatar.textContent = initials(clientId);

            const copy = document.createElement("div");
            copy.className = "collaborator-copy";

            const name = document.createElement("strong");
            name.textContent = clientId === getClientId() ? `${clientId} (you)` : clientId;

            const status = document.createElement("span");
            status.textContent = "Online";

            copy.append(name, status);
            row.append(avatar, copy);
            dom.activeClients.appendChild(row);
        });
    }

    function avatarColor(value, index) {
        const colors = ["#2563eb", "#7c3aed", "#db2777", "#0891b2", "#16a34a", "#d97706"];
        let hash = index;

        for (let i = 0; i < value.length; i += 1) {
            hash = ((hash << 5) - hash) + value.charCodeAt(i);
            hash |= 0;
        }

        return colors[Math.abs(hash) % colors.length];
    }

    function initials(value) {
        const parts = String(value).split(/[-_\s]+/).filter(Boolean);

        if (parts.length >= 2) {
            return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
        }

        return String(value).slice(0, 2).toUpperCase();
    }

    function setEditorContent(content, { remote }) {
        state.applyingRemoteChange = true;
        dom.editor.value = content || "";
        state.previousEditorContent = dom.editor.value;

        if (remote) {
            state.acknowledgedContent = dom.editor.value;
        }

        state.applyingRemoteChange = false;
        autoResizeEditor();
        updateEditorMetrics();
        updateCursorUi();
    }

    function updateVersionUi() {
        dom.currentVersion.textContent = String(state.currentVersion);
        dom.sessionVersionBadge.textContent = `v${state.currentVersion}`;
    }

    function updatePendingUi() {
        dom.pendingCount.textContent = String(state.pendingOffline.length);
    }

    function updateEditorMetrics() {
        const text = dom.editor.value;
        const trimmed = text.trim();
        const words = trimmed ? trimmed.split(/\s+/).length : 0;

        dom.wordCount.textContent = String(words);
        dom.characterCount.textContent = String(text.length);
    }

    function updateCursorUi() {
        const value = dom.editor.value;
        const cursor = dom.editor.selectionStart;
        const selected = Math.abs(dom.editor.selectionEnd - dom.editor.selectionStart);
        const beforeCursor = value.slice(0, cursor);
        const lines = beforeCursor.split("\n");
        const line = lines.length;
        const column = lines[lines.length - 1].length + 1;

        dom.cursorPosition.textContent = `Line ${line}, column ${column}`;
        dom.selectionStatus.textContent = selected > 0
            ? `${selected} character${selected === 1 ? "" : "s"} selected`
            : "No selection";
    }

    function autoResizeEditor() {
        dom.editor.style.height = "auto";
        dom.editor.style.height = `${Math.max(880, dom.editor.scrollHeight)}px`;
    }

    function indentSelection(indent) {
        const start = dom.editor.selectionStart;
        const end = dom.editor.selectionEnd;
        const value = dom.editor.value;

        dom.editor.setRangeText(indent, start, end, "end");
        dom.editor.dispatchEvent(new Event("input", { bubbles: true }));
    }

    function decreaseIndent() {
        const start = dom.editor.selectionStart;
        const value = dom.editor.value;
        const lineStart = value.lastIndexOf("\n", Math.max(0, start - 1)) + 1;
        const removable = value.slice(lineStart, lineStart + 4).match(/^ {1,4}/)?.[0] || "";

        if (!removable) {
            return;
        }

        dom.editor.setRangeText("", lineStart, lineStart + removable.length, "end");
        dom.editor.dispatchEvent(new Event("input", { bubbles: true }));
    }

    function updateConnectionUi(status, customText = null) {
        dom.connectionBadge.classList.remove("online", "connecting", "offline");
        dom.connectionBadge.classList.add(status);

        const label = customText || {
            online: "Online",
            connecting: "Connecting",
            offline: "Offline"
        }[status];

        dom.connectionStatus.textContent = label;
        dom.connectButton.disabled = status === "online" || status === "connecting";
        dom.disconnectButton.disabled = status === "offline";
    }

    function updateSaveState(type, text) {
        const container = dom.saveState.parentElement;
        container.classList.remove("syncing", "saved", "error");

        if (type === "syncing") container.classList.add("syncing");
        if (type === "saved") container.classList.add("saved");
        if (type === "error") container.classList.add("error");

        dom.saveState.textContent = text;
    }

    function updateHealth(type, text) {
        dom.healthIndicator.className = "health-indicator";

        if (type === "ready") dom.healthIndicator.classList.add("ready");
        if (type === "warning") dom.healthIndicator.classList.add("warning");
        if (type === "error") dom.healthIndicator.classList.add("error");

        dom.healthText.textContent = text;
    }

    function handleBrowserOnline() {
        showToast("Internet connection restored.", "success");
        log("Browser is online");

        if (!isSocketOpen() && !state.manuallyDisconnected && state.documentId) {
            connectWebSocket();
        }
    }

    function handleBrowserOffline() {
        showToast("You are offline. Changes will be queued locally.", "warning");
        updateConnectionUi("offline");
        updateHealth("warning", "Working offline");
        log("Browser is offline");
    }

    function handleBeforeUnload(event) {
        if (!hasUnsyncedChanges()) {
            return;
        }

        event.preventDefault();
        event.returnValue = "";
    }

    function hasUnsyncedChanges() {
        return Boolean(
            state.operationInFlight
            || state.pendingOffline.length > 0
            || dom.editor.value !== state.acknowledgedContent
        );
    }

    function isSocketOpen() {
        return state.socket?.readyState === WebSocket.OPEN;
    }

    function getDocumentId() {
        return dom.documentId.value.trim();
    }

    function getClientId() {
        return dom.clientId.value.trim();
    }

    async function copyDocumentId() {
        const documentId = getDocumentId();

        if (!documentId) {
            showToast("There is no document ID to copy.", "warning");
            return;
        }

        try {
            await navigator.clipboard.writeText(documentId);
            showToast("Document ID copied.", "success");
        } catch {
            dom.documentId.select();
            document.execCommand("copy");
            showToast("Document ID copied.", "success");
        }
    }

    async function fetchJson(url, options = {}) {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 15000);

        try {
            const response = await fetch(url, {
                ...options,
                signal: controller.signal,
                headers: {
                    Accept: "application/json",
                    ...(options.headers || {})
                }
            });

            const contentType = response.headers.get("content-type") || "";
            const body = contentType.includes("application/json")
                ? await response.json()
                : await response.text();

            if (!response.ok) {
                const message = typeof body === "object"
                    ? body.message || body.error || `Request failed with status ${response.status}`
                    : body || `Request failed with status ${response.status}`;

                throw new Error(message);
            }

            return body;
        } catch (error) {
            if (error.name === "AbortError") {
                throw new Error("The request timed out. Please try again.");
            }

            throw error;
        } finally {
            clearTimeout(timeout);
        }
    }

    function setBusy(button, busy, label) {
        button.disabled = busy;
        button.textContent = label;
    }

    function showToast(message, type = "success", duration = 3200) {
        const toast = document.createElement("div");
        toast.className = `toast ${type}`;
        toast.textContent = message;
        dom.toastRegion.appendChild(toast);

        setTimeout(() => {
            toast.remove();
        }, duration);
    }

    function log(message) {
        const time = new Date().toLocaleTimeString();
        dom.logs.textContent += `[${time}] ${message}\n`;
        dom.logs.scrollTop = dom.logs.scrollHeight;
    }

    function confirmAction(message) {
        dom.dialogMessage.textContent = message;
        dom.confirmDialog.classList.remove("hidden");

        return new Promise((resolve) => {
            state.dialogResolver = resolve;
        });
    }

    function closeDialog(result) {
        dom.confirmDialog.classList.add("hidden");

        if (state.dialogResolver) {
            state.dialogResolver(result);
            state.dialogResolver = null;
        }
    }
})();