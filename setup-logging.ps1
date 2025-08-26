# Quorus Logging Setup Script
# Sets up Grafana Loki stack for centralized logging

Write-Host "Setting up Quorus Centralized Logging..." -ForegroundColor Green

# Create necessary directories
$directories = @(
    "loki",
    "promtail", 
    "grafana/provisioning/datasources",
    "grafana/provisioning/dashboards",
    "prometheus"
)

foreach ($dir in $directories) {
    if (!(Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force
        Write-Host "Created directory: $dir" -ForegroundColor Yellow
    }
}

# Create Prometheus configuration
$prometheusConfig = @"
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  # - "first_rules.yml"
  # - "second_rules.yml"

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'quorus-api'
    static_configs:
      - targets: ['api:8080']
    metrics_path: '/q/metrics'
    scrape_interval: 5s

  - job_name: 'quorus-controllers'
    static_configs:
      - targets: ['controller1:8080', 'controller2:8080', 'controller3:8080']
    metrics_path: '/metrics'
    scrape_interval: 5s
"@

$prometheusConfig | Out-File -FilePath "prometheus/prometheus.yml" -Encoding UTF8

# Create Grafana dashboard for Quorus logs
$dashboardConfig = @"
apiVersion: 1

providers:
  - name: 'default'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
"@

$dashboardConfig | Out-File -FilePath "grafana/provisioning/dashboards/dashboards.yml" -Encoding UTF8

Write-Host "Configuration files created successfully!" -ForegroundColor Green

# Start the logging stack
Write-Host "Starting Grafana Loki logging stack..." -ForegroundColor Green
docker-compose -f docker-compose-loki.yml up -d

Write-Host ""
Write-Host "Logging stack is starting up..." -ForegroundColor Green
Write-Host "Services will be available at:" -ForegroundColor Yellow
Write-Host "  - Grafana: http://localhost:3000 (admin/admin)" -ForegroundColor Cyan
Write-Host "  - Loki: http://localhost:3100" -ForegroundColor Cyan
Write-Host "  - Prometheus: http://localhost:9090" -ForegroundColor Cyan
Write-Host ""
Write-Host "To view logs in Grafana:" -ForegroundColor Yellow
Write-Host "  1. Open http://localhost:3000" -ForegroundColor White
Write-Host "  2. Login with admin/admin" -ForegroundColor White
Write-Host "  3. Go to Explore -> Select Loki datasource" -ForegroundColor White
Write-Host "  4. Use queries like: {service=\"quorus-api\"}" -ForegroundColor White
Write-Host ""
Write-Host "Useful LogQL queries:" -ForegroundColor Yellow
Write-Host "  - All API logs: {service=\"quorus-api\"}" -ForegroundColor White
Write-Host "  - Error logs: {service=\"quorus-api\"} |= \"ERROR\"" -ForegroundColor White
Write-Host "  - Heartbeat logs: {service=\"quorus-api\"} |= \"heartbeat\"" -ForegroundColor White
Write-Host "  - Controller logs: {service=~\"quorus-controller.*\"}" -ForegroundColor White
