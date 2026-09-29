"""Minimal stdio server for testing the IDE integration, without LLVM binaries."""
import json
import re
import sys

documents = {}


def send(message):
    body = json.dumps(dict(jsonrpc="2.0", **message)).encode("utf-8")
    sys.stdout.buffer.write(f"Content-Length: {len(body)}\r\n\r\n".encode() + body)
    sys.stdout.buffer.flush()


while True:
    headers = {}
    while True:
        line = sys.stdin.buffer.readline()
        if not line:
            sys.exit(0)
        if line == b"\r\n":
            break
        key, value = line.decode().split(":", 1)
        headers[key.lower()] = value.strip()
    message = json.loads(sys.stdin.buffer.read(int(headers["content-length"])))
    method = message.get("method")
    if method == "initialize":
        send({"id": message["id"], "result": {"capabilities": {
            "textDocumentSync": 1, "completionProvider": {"triggerCharacters": ["."]},
        }}})
    elif method == "textDocument/completion":
        params = message["params"]
        position = params["position"]
        text = documents[params["textDocument"]["uri"]]
        before = text.splitlines()[position["line"]][:position["character"]]
        # Match MLIR's suffix-only results (no textEdit or insertText).
        if re.search(r"![a-zA-Z0-9_]*$", before):
            names = ["builtin", "func", "memref"]
            if "!scalar = f32" in text:
                names.append("scalar")
            detail = "type dialect or alias"
        elif before.endswith(": "):
            names = ["f32", "f64", "index", "tensor", "memref"]
            detail = "type"
        else:
            names = ["add", "sub", "mul", "intr.sqrt"]
            detail = "operation"
        send({"id": message["id"], "result": {"isIncomplete": False, "items": [
            {"label": name, "kind": 5, "detail": detail, "insertTextFormat": 1}
            for name in names
        ]}})
    elif method in ("textDocument/didOpen", "textDocument/didChange"):
        params = message["params"]
        document = params["textDocument"]
        text = document["text"] if method.endswith("didOpen") else params["contentChanges"][-1]["text"]
        documents[document["uri"]] = text
        diagnostics = []
        if "LSP_TEST_ERROR" in text:
            diagnostics.append({
                "range": {"start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 1}},
                "severity": 1, "message": "MLIR LSP integration diagnostic", "source": "test server",
            })
        send({"method": "textDocument/publishDiagnostics", "params": {
            "uri": document["uri"], "version": document["version"], "diagnostics": diagnostics,
        }})
    elif method == "exit":
        break
    elif "id" in message:
        send({"id": message["id"], "result": None})
