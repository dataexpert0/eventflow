
param(
    [string]$RabbitUrl = "http://127.0.0.1:15672",
    [string]$Username = "eventflow"
)

$ErrorActionPreference = "Stop"

$password = $env:RABBITMQ_PASSWORD

if ([string]::IsNullOrWhiteSpace($password)) {
    throw "RABBITMQ_PASSWORD is not configured."
}

$policyName = "eventflow-notifications-dlx"

$policy = @{
    pattern = '^eventflow\.notifications\.orders\.q$'
    "apply-to" = "queues"
    priority = 1
    definition = @{
        "dead-letter-exchange" = "eventflow.notifications.dlx"
        "dead-letter-routing-key" = "notifications.orders.dead"
    }
}

$json = $policy | ConvertTo-Json -Depth 5

$credentials = "${Username}:${password}"

$encoded = [Convert]::ToBase64String(
    [Text.Encoding]::UTF8.GetBytes($credentials)
)

$headers = @{
    Authorization = "Basic $encoded"
}

$uri = "$RabbitUrl/api/policies/%2F/$policyName"

Write-Host "Configuring RabbitMQ dead-letter policy..."

Invoke-RestMethod `
    -Uri $uri `
    -Method Put `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $json | Out-Null

Write-Host "RabbitMQ DLQ policy configured successfully."