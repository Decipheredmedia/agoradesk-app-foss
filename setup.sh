#!/bin/bash

# AgoraDesk/LocalMonero Setup Script
# This script automates the initial setup process

set -e

echo "================================================"
echo "AgoraDesk/LocalMonero Android App Setup"
echo "================================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Print colored messages
print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}!${NC} $1"
}

print_info() {
    echo -e "${GREEN}→${NC} $1"
}

# Check prerequisites
echo "Checking prerequisites..."
echo ""

MISSING_DEPS=0

if command_exists java; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    print_success "Java installed: $JAVA_VERSION"
else
    print_error "Java not found. Please install JDK 17"
    MISSING_DEPS=1
fi

if command_exists docker; then
    DOCKER_VERSION=$(docker --version | awk '{print $3}')
    print_success "Docker installed: $DOCKER_VERSION"
else
    print_warning "Docker not found. Required for backend nodes"
fi

if command_exists docker-compose; then
    COMPOSE_VERSION=$(docker-compose --version | awk '{print $3}')
    print_success "Docker Compose installed: $COMPOSE_VERSION"
else
    print_warning "Docker Compose not found. Required for backend nodes"
fi

if command_exists flutter; then
    FLUTTER_VERSION=$(flutter --version | head -n 1 | awk '{print $2}')
    print_success "Flutter installed: $FLUTTER_VERSION"
else
    print_warning "Flutter not found. Required for Flutter components"
fi

if [ $MISSING_DEPS -eq 1 ]; then
    print_error "Missing required dependencies. Please install them first."
    exit 1
fi

echo ""
echo "================================================"
echo "Step 1: Environment Configuration"
echo "================================================"
echo ""

# Check if .env exists
if [ -f ".env" ]; then
    print_warning ".env file already exists"
    read -p "Do you want to overwrite it? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        cp .env.template .env
        print_success "Created new .env file from template"
    fi
else
    cp .env.template .env
    print_success "Created .env file from template"
fi

echo ""
print_info "Please edit .env file with your configuration:"
echo "  - BITCOIN_RPC_USER and BITCOIN_RPC_PASSWORD"
echo "  - MONERO_RPC_USER and MONERO_RPC_PASSWORD"
echo "  - MAPBOX_API_KEY"
echo "  - JWT_SECRET_KEY"
echo ""

read -p "Press Enter when you've configured .env file..."

echo ""
echo "================================================"
echo "Step 2: MapBox API Key"
echo "================================================"
echo ""

if [ -f "lib/keys/keys.dart" ]; then
    print_warning "lib/keys/keys.dart already exists"
else
    mkdir -p lib/keys
    cp lib/keys/keys.dart.template lib/keys/keys.dart
    print_success "Created lib/keys/keys.dart from template"
    echo ""
    print_info "Please edit lib/keys/keys.dart with your MapBox API key"
    print_info "Get your key from: https://www.mapbox.com/"
    echo ""
    read -p "Press Enter when you've added your MapBox API key..."
fi

echo ""
echo "================================================"
echo "Step 3: Signing Keys (Optional for Debug)"
echo "================================================"
echo ""

read -p "Do you want to create release signing keys? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_info "Creating AgoraDesk keystore..."
    keytool -genkey -v -keystore android/agoradesk-release-key.jks \
        -keyalg RSA -keysize 2048 -validity 10000 -alias agoradesk
    
    print_info "Creating LocalMonero keystore..."
    keytool -genkey -v -keystore android/localmonero-release-key.jks \
        -keyalg RSA -keysize 2048 -validity 10000 -alias localmonero
    
    print_success "Signing keys created"
    print_warning "Don't forget to update android/key.properties with your keystore passwords"
fi

echo ""
echo "================================================"
echo "Step 4: Docker Backend Setup"
echo "================================================"
echo ""

if command_exists docker && command_exists docker-compose; then
    read -p "Do you want to start Bitcoin and Monero nodes? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "Starting Docker containers..."
        docker-compose up -d
        
        echo ""
        print_success "Docker containers started"
        print_info "Waiting for services to initialize..."
        sleep 5
        
        echo ""
        print_info "Container status:"
        docker-compose ps
        
        echo ""
        print_warning "Initial blockchain sync may take several hours"
        print_info "Monitor logs with: docker-compose logs -f bitcoin"
    fi
else
    print_warning "Skipping Docker setup (Docker not available)"
fi

echo ""
echo "================================================"
echo "Step 5: Install Flutter Dependencies"
echo "================================================"
echo ""

if command_exists flutter; then
    print_info "Running flutter pub get..."
    flutter pub get
    
    print_info "Running build_runner..."
    dart run build_runner build --delete-conflicting-outputs
    
    print_success "Flutter dependencies installed"
else
    print_warning "Skipping Flutter setup (Flutter not available)"
fi

echo ""
echo "================================================"
echo "Setup Complete!"
echo "================================================"
echo ""

print_success "Initial setup is complete"
echo ""
echo "Next steps:"
echo ""
echo "1. Build debug APK:"
echo "   cd android"
echo "   ./gradlew assembleAgoradeskDebug"
echo ""
echo "2. Or use Flutter:"
echo "   flutter build apk --flavor agoradesk"
echo ""
echo "3. View documentation:"
echo "   - ANDROID_NATIVE_INTEGRATION.md for native Android"
echo "   - PRODUCTION_DEPLOYMENT.md for production setup"
echo "   - TROUBLESHOOTING.md for common issues"
echo ""
echo "4. Monitor Docker logs (if running):"
echo "   docker-compose logs -f"
echo ""

print_info "Happy coding! 🚀"
