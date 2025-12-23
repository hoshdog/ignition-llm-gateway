# Setup Claude Desktop configuration for Ignition MCP Server

$appdata = [Environment]::GetFolderPath('ApplicationData')
$claudeDir = Join-Path $appdata 'Claude'
$configFile = Join-Path $claudeDir 'claude_desktop_config.json'

# Create Claude directory if it doesn't exist
if (-not (Test-Path $claudeDir)) {
    New-Item -ItemType Directory -Force -Path $claudeDir | Out-Null
    Write-Host "Created Claude config directory: $claudeDir"
}

# Get current script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$mcpServerPath = Join-Path $scriptDir 'build\index.js'

# Configuration content
$config = @{
    mcpServers = @{
        "ignition-gateway" = @{
            command = "node"
            args = @($mcpServerPath)
            env = @{
                IGNITION_GATEWAY_URL = "http://localhost:8089"
                IGNITION_API_KEY = "llmgw_KNp3DcEsfxsV3tBF6xMPn26fSmH5yCcHlKk82yHXyAQ"
            }
        }
    }
}

# Check if config file already exists
if (Test-Path $configFile) {
    Write-Host "Config file already exists: $configFile"
    Write-Host "Reading existing configuration..."
    $existingConfig = Get-Content $configFile | ConvertFrom-Json -AsHashtable

    # Merge with existing config
    if (-not $existingConfig.mcpServers) {
        $existingConfig.mcpServers = @{}
    }
    $existingConfig.mcpServers["ignition-gateway"] = $config.mcpServers["ignition-gateway"]
    $config = $existingConfig
}

# Write config file
$configJson = $config | ConvertTo-Json -Depth 10
$configJson | Out-File -FilePath $configFile -Encoding utf8
Write-Host "Configuration written to: $configFile"

Write-Host ""
Write-Host "=== Claude Desktop Configuration Complete ===" -ForegroundColor Green
Write-Host "MCP Server Path: $mcpServerPath"
Write-Host "Gateway URL: http://localhost:8089"
Write-Host ""
Write-Host "Next steps:"
Write-Host "1. Restart Claude Desktop completely"
Write-Host "2. Look for the tools icon in the chat interface"
Write-Host "3. Try: 'Check the health of my Ignition Gateway'"
