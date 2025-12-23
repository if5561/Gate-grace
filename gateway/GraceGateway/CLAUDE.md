# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GraceGateway is a custom, high-performance API gateway built on Netty 4.1.51.Final. It uses Java 17 and follows a multi-module Maven structure with SPI-based extensibility.

## Essential Commands

### Building
```bash
mvn clean install              # Full build with tests
mvn clean install -DskipTests  # Build without tests
```

### Testing
```bash
mvn test                       # Run all tests
mvn test -pl GraceGateway-Core # Test specific module
mvn test -Dtest=TestSimple -pl GraceGateway-Core # Run specific test
```

### Running the Gateway

1. **Start Nacos** (required for configuration/discovery):
   - Start Nacos server 2.2.1 at 127.0.0.1:8848
   - Create config with Data ID: `grace-gateway-data`

2. **Start downstream service**:
   ```bash
   cd GraceGateway-User
   mvn clean package spring-boot:run
   ```

3. **Start gateway**:
   ```bash
   cd GraceGateway-Demo
   mvn clean compile exec:java -Dexec.mainClass="com.grace.gateway.demo.Main"
   ```

## Architecture

### Key Design Patterns
- **Filter Chain**: Core request processing using Chain of Responsibility
- **SPI Plugin System**: Extensibility for filters, config centers, register centers
- **Async Non-blocking**: All I/O operations use Netty's event-driven model

### Request Flow
```
Client → NettyHttpServer → NettyCoreProcessor → GatewayContext → FilterChain
                                                          ↓
Backend Services ← AsyncHttpClient ← LoadBalancer ← Pre-filters
```

### Module Structure
- **GraceGateway-Core**: Request processing pipeline, filter chain, context management
- **GraceGateway-Bootstrap**: Gateway initialization and lifecycle
- **GraceGateway-Config**: Dynamic configuration management (Nacos/Zookeeper)
- **GraceGateway-Register**: Service discovery abstractions
- **GraceGateway-Common**: Shared constants and utilities
- **GraceGateway-Demo**: Example gateway application (port 9999)

### Extension Points

**Custom Filters**:
1. Implement `Filter` interface with `doPreFilter()` and `doPostFilter()`
2. Define `mark()` for identification and `getOrder()` for execution order
3. Package with ServiceLoader configuration file

**Custom Load Balancing**:
- Strategy pattern implementation for load balancing algorithms
- Support for both gray release and normal traffic scenarios

**Custom Configuration Center**:
- Implement `ConfigCenterProcessor` interface
- Handle route/service/instance updates with observer pattern

### Important Implementation Details

- **Thread Safety**: All configuration changes use ConcurrentHashMap and atomic operations
- **Resource Management**: Use Netty's ReferenceCountUtil for ByteBuf lifecycle
- **Error Handling**: Custom exception hierarchy flows through NettyCoreProcessor
- **Dynamic Updates**: Routes and filters can be modified at runtime without restart

### Key Files for Understanding Flow
- `Bootstrap.java`: Entry point and initialization
- `NettyCoreProcessor.java`: Main request processing logic
- `FilterChainFactory.java`: Filter loading and management
- `DynamicConfigManager.java`: Singleton configuration management
- `GatewayContext.java`: Request context carrying state through filters