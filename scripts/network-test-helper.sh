#!/bin/bash

# Quorus Network Testing Helper Script
# Provides utilities for managing Docker networks and testing network scenarios

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Network names
MAIN_NETWORK="quorus_raft-cluster"
PARTITION_A="quorus_raft-partition-a"
PARTITION_B="quorus_raft-partition-b"

# Container names
CONTROLLERS=("quorus-controller1" "quorus-controller2" "quorus-controller3" "quorus-controller4" "quorus-controller5")

print_usage() {
    echo "Usage: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  setup           - Set up Docker networks for testing"
    echo "  cleanup         - Clean up Docker networks and containers"
    echo "  status          - Show network and container status"
    echo "  partition       - Create network partition"
    echo "  restore         - Restore network connectivity"
    echo "  latency         - Add network latency to nodes"
    echo "  packet-loss     - Add packet loss to nodes"
    echo "  isolate         - Isolate a specific node"
    echo "  monitor         - Monitor cluster health"
    echo ""
    echo "Examples:"
    echo "  $0 setup"
    echo "  $0 partition 1,2,3 4,5"
    echo "  $0 latency controller1 100ms"
    echo "  $0 packet-loss controller2 5%"
    echo "  $0 isolate controller3"
    echo "  $0 restore"
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed or not in PATH"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running"
        exit 1
    fi
}

setup_networks() {
    log_info "Setting up Docker networks for Raft testing..."
    
    cd "$PROJECT_ROOT"
    
    # Start the network test environment
    docker-compose -f docker-compose-network-test.yml up -d
    
    log_success "Docker networks and containers are ready"
    
    # Wait for containers to be healthy
    log_info "Waiting for containers to be healthy..."
    sleep 30
    
    show_status
}

cleanup_networks() {
    log_info "Cleaning up Docker networks and containers..."
    
    cd "$PROJECT_ROOT"
    
    # Stop and remove containers
    docker-compose -f docker-compose-network-test.yml down -v
    
    # Remove any orphaned networks
    docker network prune -f
    
    log_success "Cleanup completed"
}

show_status() {
    log_info "Cluster Status:"
    echo ""
    
    # Check container status
    echo "Container Status:"
    for container in "${CONTROLLERS[@]}"; do
        if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -q "$container"; then
            status=$(docker ps --format "{{.Status}}" --filter "name=$container")
            echo -e "  ${GREEN}✓${NC} $container: $status"
        else
            echo -e "  ${RED}✗${NC} $container: Not running"
        fi
    done
    
    echo ""
    echo "Network Status:"
    
    # Check network connectivity
    for i in {1..5}; do
        container="quorus-controller$i"
        port=$((8080 + i))
        
        if curl -s -f "http://localhost:$port/health" > /dev/null 2>&1; then
            echo -e "  ${GREEN}✓${NC} Controller $i: Healthy (http://localhost:$port)"
        else
            echo -e "  ${RED}✗${NC} Controller $i: Unhealthy or unreachable"
        fi
    done
    
    echo ""
    echo "Raft Status:"
    
    # Check Raft status
    for i in {1..5}; do
        port=$((8080 + i))
        
        if status=$(curl -s "http://localhost:$port/raft/status" 2>/dev/null); then
            state=$(echo "$status" | grep -o '"state":"[^"]*"' | cut -d'"' -f4)
            term=$(echo "$status" | grep -o '"term":[0-9]*' | cut -d':' -f2)
            echo -e "  Controller $i: State=$state, Term=$term"
        else
            echo -e "  Controller $i: ${RED}Unable to get Raft status${NC}"
        fi
    done
}

create_partition() {
    if [ $# -ne 2 ]; then
        log_error "Usage: partition <group1> <group2>"
        log_error "Example: partition 1,2,3 4,5"
        exit 1
    fi
    
    local group1="$1"
    local group2="$2"
    
    log_info "Creating network partition: Group1($group1) vs Group2($group2)"
    
    # Convert comma-separated lists to arrays
    IFS=',' read -ra nodes1 <<< "$group1"
    IFS=',' read -ra nodes2 <<< "$group2"
    
    # Move group1 to partition-a network
    for node in "${nodes1[@]}"; do
        container="quorus-controller$node"
        log_info "Moving $container to partition-a network"
        
        docker network disconnect "$MAIN_NETWORK" "$container" 2>/dev/null || true
        docker network connect --ip "172.21.0.1$node" "$PARTITION_A" "$container"
    done
    
    # Move group2 to partition-b network
    for node in "${nodes2[@]}"; do
        container="quorus-controller$node"
        log_info "Moving $container to partition-b network"
        
        docker network disconnect "$MAIN_NETWORK" "$container" 2>/dev/null || true
        docker network connect --ip "172.22.0.1$node" "$PARTITION_B" "$container"
    done
    
    log_success "Network partition created"
    
    # Show status after partition
    sleep 5
    show_status
}

restore_connectivity() {
    log_info "Restoring network connectivity..."
    
    for i in {1..5}; do
        container="quorus-controller$i"
        
        # Disconnect from partition networks
        docker network disconnect "$PARTITION_A" "$container" 2>/dev/null || true
        docker network disconnect "$PARTITION_B" "$container" 2>/dev/null || true
        
        # Reconnect to main network
        docker network connect --ip "172.20.0.1$i" "$MAIN_NETWORK" "$container" 2>/dev/null || true
    done
    
    log_success "Network connectivity restored"
    
    # Show status after restoration
    sleep 5
    show_status
}

add_latency() {
    if [ $# -ne 2 ]; then
        log_error "Usage: latency <controller> <delay>"
        log_error "Example: latency controller1 100ms"
        exit 1
    fi
    
    local container="quorus-$1"
    local delay="$2"
    
    log_info "Adding $delay latency to $container"
    
    docker exec "$container" tc qdisc add dev eth0 root netem delay "$delay" 2>/dev/null || {
        log_warning "Failed to add latency (tc might not be available or already configured)"
    }
    
    log_success "Latency added to $container"
}

add_packet_loss() {
    if [ $# -ne 2 ]; then
        log_error "Usage: packet-loss <controller> <percentage>"
        log_error "Example: packet-loss controller2 5%"
        exit 1
    fi
    
    local container="quorus-$1"
    local loss="$2"
    
    log_info "Adding $loss packet loss to $container"
    
    docker exec "$container" tc qdisc add dev eth0 root netem loss "$loss" 2>/dev/null || {
        log_warning "Failed to add packet loss (tc might not be available or already configured)"
    }
    
    log_success "Packet loss added to $container"
}

isolate_node() {
    if [ $# -ne 1 ]; then
        log_error "Usage: isolate <controller>"
        log_error "Example: isolate controller3"
        exit 1
    fi
    
    local container="quorus-$1"
    
    log_info "Isolating $container from network"
    
    # Block all traffic
    docker exec "$container" iptables -A INPUT -j DROP 2>/dev/null || true
    docker exec "$container" iptables -A OUTPUT -j DROP 2>/dev/null || true
    
    log_success "Node $container isolated"
}

monitor_cluster() {
    log_info "Starting cluster monitoring (Press Ctrl+C to stop)..."
    
    while true; do
        clear
        echo "=== Quorus Raft Cluster Monitor ==="
        echo "$(date)"
        echo ""
        
        show_status
        
        sleep 5
    done
}

# Main script logic
case "${1:-}" in
    setup)
        check_docker
        setup_networks
        ;;
    cleanup)
        check_docker
        cleanup_networks
        ;;
    status)
        check_docker
        show_status
        ;;
    partition)
        check_docker
        create_partition "$2" "$3"
        ;;
    restore)
        check_docker
        restore_connectivity
        ;;
    latency)
        check_docker
        add_latency "$2" "$3"
        ;;
    packet-loss)
        check_docker
        add_packet_loss "$2" "$3"
        ;;
    isolate)
        check_docker
        isolate_node "$2"
        ;;
    monitor)
        check_docker
        monitor_cluster
        ;;
    *)
        print_usage
        exit 1
        ;;
esac
