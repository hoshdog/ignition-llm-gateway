# Integration Examples

This document provides client examples for integrating with the Ignition LLM Gateway module in various programming languages.

## Python Client

### Full-Featured Client Class

```python
import requests
import json
import uuid
from typing import Optional, Dict, Any, List

class IgnitionLLMClient:
    """Python client for the Ignition LLM Gateway module."""

    def __init__(self, gateway_url: str, api_key: str):
        """
        Initialize the client.

        Args:
            gateway_url: Base URL of the Ignition Gateway (e.g., "http://localhost:8088")
            api_key: API key with appropriate permissions
        """
        self.gateway_url = gateway_url.rstrip('/')
        self.api_key = api_key
        self.conversation_id: Optional[str] = None

    def _headers(self) -> Dict[str, str]:
        """Get request headers with authorization."""
        return {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

    def health(self) -> Dict[str, Any]:
        """Check gateway health status."""
        response = requests.get(f"{self.gateway_url}/system/llm-gateway/health")
        response.raise_for_status()
        return response.json()

    def chat(self, message: str, new_conversation: bool = False) -> Dict[str, Any]:
        """
        Send a chat message and get a response.

        Args:
            message: Natural language message to send
            new_conversation: If True, start a new conversation

        Returns:
            Response dictionary with 'message', 'conversationId', and 'actionResults'
        """
        payload = {"message": message}
        if self.conversation_id and not new_conversation:
            payload["conversationId"] = self.conversation_id

        response = requests.post(
            f"{self.gateway_url}/system/llm-gateway/api/v1/chat",
            headers=self._headers(),
            json=payload
        )
        response.raise_for_status()
        data = response.json()
        self.conversation_id = data.get("conversationId")
        return data

    def action(
        self,
        action_type: str,
        resource_type: str,
        resource_path: str,
        payload: Optional[Dict[str, Any]] = None,
        options: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Execute a direct action.

        Args:
            action_type: "read", "create", "update", or "delete"
            resource_type: "tag", "perspective-view", "script", "named-query", "project"
            resource_path: Path to the resource
            payload: Optional data payload for create/update
            options: Optional options like dryRun, force, comment

        Returns:
            Action result dictionary
        """
        request_body = {
            "correlationId": str(uuid.uuid4()),
            "action": action_type,
            "resourceType": resource_type,
            "resourcePath": resource_path
        }
        if payload:
            request_body["payload"] = payload
        if options:
            request_body["options"] = options

        response = requests.post(
            f"{self.gateway_url}/system/llm-gateway/api/v1/action",
            headers=self._headers(),
            json=request_body
        )
        response.raise_for_status()
        return response.json()

    # Convenience methods for common operations

    def list_tag_providers(self) -> List[str]:
        """List all tag providers."""
        result = self.action("read", "tag", "*")
        if result.get("status") == "SUCCESS":
            return result.get("data", {}).get("providers", [])
        return []

    def browse_tags(self, path: str = "[default]") -> List[Dict[str, Any]]:
        """Browse tags at the given path."""
        result = self.action("read", "tag", f"{path}/*")
        if result.get("status") == "SUCCESS":
            return result.get("data", {}).get("children", [])
        return []

    def read_tag(self, path: str) -> Dict[str, Any]:
        """Read a specific tag's value and configuration."""
        return self.action("read", "tag", path)

    def write_tag(self, path: str, value: Any) -> Dict[str, Any]:
        """Write a value to a tag."""
        return self.action("update", "tag", path, payload={"value": value})

    def create_tag(
        self,
        path: str,
        tag_type: str = "AtomicTag",
        data_type: str = "Float8",
        value: Any = None
    ) -> Dict[str, Any]:
        """Create a new tag."""
        payload = {"tagType": tag_type, "dataType": data_type}
        if value is not None:
            payload["value"] = value
        return self.action("create", "tag", path, payload=payload)

    def delete_tag(self, path: str, force: bool = True) -> Dict[str, Any]:
        """Delete a tag."""
        return self.action("delete", "tag", path, options={"force": force})

    def list_views(self, project: str) -> List[Dict[str, Any]]:
        """List all views in a project."""
        result = self.action("read", "perspective-view", f"{project}/*")
        if result.get("status") == "SUCCESS":
            return result.get("data", {}).get("views", [])
        return []

    def list_scripts(self, project: str) -> List[Dict[str, Any]]:
        """List all scripts in a project."""
        result = self.action("read", "script", f"{project}/*")
        if result.get("status") == "SUCCESS":
            return result.get("data", {}).get("scripts", [])
        return []

    def list_queries(self, project: str) -> List[Dict[str, Any]]:
        """List all named queries in a project."""
        result = self.action("read", "named-query", f"{project}/*")
        if result.get("status") == "SUCCESS":
            return result.get("data", {}).get("queries", [])
        return []


# Usage Examples

if __name__ == "__main__":
    # Initialize client
    client = IgnitionLLMClient(
        gateway_url="http://localhost:8088",
        api_key="llmgw_your_key_here"
    )

    # Check health
    print("Health:", client.health())

    # Chat interaction
    response = client.chat("List all tag providers")
    print("Chat response:", response["message"])

    # Continue conversation
    response = client.chat("Show me the tags in the default provider")
    print("Follow-up:", response["message"])

    # Direct actions
    providers = client.list_tag_providers()
    print("Providers:", providers)

    tags = client.browse_tags("[default]")
    print("Tags:", tags)

    # Create a tag
    result = client.create_tag(
        path="[default]MyFolder/Temperature",
        data_type="Float8",
        value=72.5
    )
    print("Created:", result)

    # Read the tag
    tag = client.read_tag("[default]MyFolder/Temperature")
    print("Tag value:", tag)

    # Update the tag
    result = client.write_tag("[default]MyFolder/Temperature", 85.0)
    print("Updated:", result)

    # Delete the tag
    result = client.delete_tag("[default]MyFolder/Temperature")
    print("Deleted:", result)
```

### Simple Script Example

```python
import requests

GATEWAY = "http://localhost:8088"
API_KEY = "llmgw_your_key_here"

# Simple chat
response = requests.post(
    f"{GATEWAY}/system/llm-gateway/api/v1/chat",
    headers={
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    },
    json={"message": "Create a Float8 tag called TestTag in the default provider with value 42"}
)

print(response.json()["message"])
```

---

## JavaScript/Node.js Client

### ES6 Module Client

```javascript
/**
 * Ignition LLM Gateway Client for Node.js
 */
class IgnitionLLMClient {
  constructor(gatewayUrl, apiKey) {
    this.gatewayUrl = gatewayUrl.replace(/\/$/, '');
    this.apiKey = apiKey;
    this.conversationId = null;
  }

  async _fetch(endpoint, options = {}) {
    const response = await fetch(`${this.gatewayUrl}${endpoint}`, {
      ...options,
      headers: {
        'Authorization': `Bearer ${this.apiKey}`,
        'Content-Type': 'application/json',
        ...options.headers
      }
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(`HTTP ${response.status}: ${error}`);
    }

    return response.json();
  }

  async health() {
    const response = await fetch(`${this.gatewayUrl}/system/llm-gateway/health`);
    return response.json();
  }

  async chat(message, newConversation = false) {
    const payload = { message };
    if (this.conversationId && !newConversation) {
      payload.conversationId = this.conversationId;
    }

    const data = await this._fetch('/system/llm-gateway/api/v1/chat', {
      method: 'POST',
      body: JSON.stringify(payload)
    });

    this.conversationId = data.conversationId;
    return data;
  }

  async action(actionType, resourceType, resourcePath, payload = null, options = null) {
    const body = {
      correlationId: crypto.randomUUID(),
      action: actionType,
      resourceType,
      resourcePath
    };
    if (payload) body.payload = payload;
    if (options) body.options = options;

    return this._fetch('/system/llm-gateway/api/v1/action', {
      method: 'POST',
      body: JSON.stringify(body)
    });
  }

  // Convenience methods
  async listTagProviders() {
    const result = await this.action('read', 'tag', '*');
    return result.status === 'SUCCESS' ? result.data?.providers || [] : [];
  }

  async browseTags(path = '[default]') {
    const result = await this.action('read', 'tag', `${path}/*`);
    return result.status === 'SUCCESS' ? result.data?.children || [] : [];
  }

  async readTag(path) {
    return this.action('read', 'tag', path);
  }

  async writeTag(path, value) {
    return this.action('update', 'tag', path, { value });
  }

  async createTag(path, tagType = 'AtomicTag', dataType = 'Float8', value = null) {
    const payload = { tagType, dataType };
    if (value !== null) payload.value = value;
    return this.action('create', 'tag', path, payload);
  }

  async deleteTag(path, force = true) {
    return this.action('delete', 'tag', path, null, { force });
  }
}

// Usage
const client = new IgnitionLLMClient('http://localhost:8088', 'llmgw_your_key');

// Chat
const response = await client.chat('List all projects');
console.log(response.message);

// Actions
const tags = await client.browseTags('[default]');
console.log('Tags:', tags);

export default IgnitionLLMClient;
```

### Browser Fetch Example

```javascript
const GATEWAY = 'http://localhost:8088';
const API_KEY = 'llmgw_your_key';

// Simple chat request
async function chat(message) {
  const response = await fetch(`${GATEWAY}/system/llm-gateway/api/v1/chat`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${API_KEY}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ message })
  });

  const data = await response.json();
  return data.message;
}

// Usage
chat('Show me all tag providers').then(console.log);
```

---

## cURL Examples

### Health Check

```bash
curl http://localhost:8088/system/llm-gateway/health
```

### Configure Claude Provider

```bash
curl -u admin:password -X POST \
  http://localhost:8088/system/llm-gateway/admin/providers/claude/config \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "sk-ant-api03-YOUR_KEY",
    "defaultModel": "claude-sonnet-4-20250514",
    "enabled": true
  }'
```

### Create API Key

```bash
curl -u admin:password -X POST \
  http://localhost:8088/system/llm-gateway/admin/api-keys \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-integration",
    "permissions": ["tag:read", "tag:create", "tag:update", "project:read"]
  }'
```

### Chat Endpoint

```bash
# Single message
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/chat \
  -H "Authorization: Bearer llmgw_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"message": "List all tag providers"}'

# Multi-turn conversation
CONV_ID="your-conversation-id"
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/chat \
  -H "Authorization: Bearer llmgw_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"conversationId\": \"$CONV_ID\", \"message\": \"Create a tag folder called Test\"}"
```

### Direct Action Endpoint

```bash
# List tag providers
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer llmgw_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "test-123",
    "action": "read",
    "resourceType": "tag",
    "resourcePath": "*"
  }'

# Create a tag
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer llmgw_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "test-456",
    "action": "create",
    "resourceType": "tag",
    "resourcePath": "[default]MyFolder/Temperature",
    "payload": {
      "tagType": "AtomicTag",
      "dataType": "Float8",
      "value": 72.5
    }
  }'

# Create a Perspective view
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer llmgw_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "test-789",
    "action": "create",
    "resourceType": "perspective-view",
    "resourcePath": "my_project/Dashboard",
    "payload": {
      "root": {
        "type": "ia.container.flex",
        "children": []
      }
    }
  }'

# Create a script
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer llmgw_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "test-abc",
    "action": "create",
    "resourceType": "script",
    "resourcePath": "my_project/library/Utils/helpers",
    "payload": {
      "code": "def greet(name):\n    return \"Hello, \" + name"
    }
  }'

# Create a named query
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer llmgw_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "test-def",
    "action": "create",
    "resourceType": "named-query",
    "resourcePath": "my_project/GetActiveAlarms",
    "payload": {
      "query": "SELECT * FROM alarms WHERE active = 1",
      "queryType": "Query",
      "database": "default"
    }
  }'

# Delete with force
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer llmgw_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "test-del",
    "action": "delete",
    "resourceType": "tag",
    "resourcePath": "[default]MyFolder",
    "options": {"force": true}
  }'
```

---

## PowerShell Examples

```powershell
$Gateway = "http://localhost:8088"
$ApiKey = "llmgw_YOUR_KEY"
$Headers = @{
    "Authorization" = "Bearer $ApiKey"
    "Content-Type" = "application/json"
}

# Health check
Invoke-RestMethod -Uri "$Gateway/system/llm-gateway/health"

# Chat
$Body = @{
    message = "List all tag providers"
} | ConvertTo-Json

$Response = Invoke-RestMethod -Uri "$Gateway/system/llm-gateway/api/v1/chat" `
    -Method POST `
    -Headers $Headers `
    -Body $Body

Write-Host $Response.message

# Direct action
$ActionBody = @{
    correlationId = [guid]::NewGuid().ToString()
    action = "read"
    resourceType = "tag"
    resourcePath = "[default]/*"
} | ConvertTo-Json

$Result = Invoke-RestMethod -Uri "$Gateway/system/llm-gateway/api/v1/action" `
    -Method POST `
    -Headers $Headers `
    -Body $ActionBody

$Result.data.children | Format-Table
```

---

## Ignition Script Examples

### Gateway Message Handler

```python
# In a Gateway Message Handler that receives LLM commands
def handleMessage(payload):
    import system.net

    gateway_url = "http://localhost:8088"
    api_key = "llmgw_YOUR_KEY"

    headers = {
        "Authorization": "Bearer " + api_key,
        "Content-Type": "application/json"
    }

    body = system.util.jsonEncode({
        "message": payload.get("command", "List projects")
    })

    response = system.net.httpPost(
        gateway_url + "/system/llm-gateway/api/v1/chat",
        headers,
        body
    )

    return system.util.jsonDecode(response)
```

### Perspective Component Script

```python
# Button action handler to chat with LLM Gateway
def onActionPerformed(self, event):
    import system.net

    message = self.getSibling("ChatInput").props.text

    response = system.net.httpPost(
        "http://localhost:8088/system/llm-gateway/api/v1/chat",
        {
            "Authorization": "Bearer llmgw_YOUR_KEY",
            "Content-Type": "application/json"
        },
        system.util.jsonEncode({"message": message})
    )

    result = system.util.jsonDecode(response)
    self.getSibling("ChatOutput").props.text = result.get("message", "No response")
```

---

## Error Handling

### Python Exception Handling

```python
import requests
from requests.exceptions import RequestException

def safe_chat(client, message):
    try:
        return client.chat(message)
    except requests.exceptions.HTTPError as e:
        if e.response.status_code == 401:
            print("Authentication failed - check API key")
        elif e.response.status_code == 403:
            print("Permission denied - check API key permissions")
        elif e.response.status_code == 500:
            print("Server error - check gateway logs")
        raise
    except RequestException as e:
        print(f"Network error: {e}")
        raise
```

### JavaScript Error Handling

```javascript
async function safeChat(client, message) {
  try {
    return await client.chat(message);
  } catch (error) {
    if (error.message.includes('401')) {
      console.error('Authentication failed - check API key');
    } else if (error.message.includes('403')) {
      console.error('Permission denied - check API key permissions');
    } else if (error.message.includes('500')) {
      console.error('Server error - check gateway logs');
    }
    throw error;
  }
}
```

---

## Best Practices

1. **API Key Security**
   - Store keys in environment variables, not in code
   - Use least-privilege permissions
   - Rotate keys periodically

2. **Error Handling**
   - Always check response status codes
   - Handle network timeouts gracefully
   - Log correlation IDs for debugging

3. **Performance**
   - Reuse client instances
   - Use connection pooling for high-volume integrations
   - Consider caching for repeated queries

4. **Conversation Management**
   - Start new conversations for unrelated tasks
   - Preserve conversation IDs for multi-turn interactions
   - Clear conversation context periodically for long-running sessions
