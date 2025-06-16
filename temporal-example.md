# Temporal Integration in Ordering System

## Overview

This ordering system uses Temporal to orchestrate complex order processing workflows while maintaining order data in MySQL. The integration provides reliable, fault-tolerant order processing with proper state management and compensation logic.

## Architecture

### Dual State Management

The system maintains order state in two places:
1. **MySQL Database**: Persistent order records with business data
2. **Temporal Workflows**: Process state and execution history

These are linked via the `temporal_workflow_id` field in the `orders` table.

```sql
-- orders table schema
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  order_date DATETIME NOT NULL,
  status ENUM('PENDING', 'VALIDATING', 'RESERVING_STOCK', 'PROCESSING_PAYMENT', 'CONFIRMED', 'COMPLETED', 'FAILED', 'CANCELLED'),
  total_amount DECIMAL(10,2) NOT NULL,
  temporal_workflow_id VARCHAR(255), -- Links to Temporal workflow
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## Temporal Configuration

### Infrastructure Setup

**Docker Compose Stack**:
- **ScyllaDB**: Temporal's persistence layer (high-performance Cassandra alternative)
- **Elasticsearch**: Workflow search and visibility
- **Temporal Server**: Core workflow engine on port 7233
- **Temporal UI**: Web interface on port 8081

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: ordering_system_mysql
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword # Change in production
      MYSQL_DATABASE: ordering_system
      MYSQL_USER: user
      MYSQL_PASSWORD: password # Change in production
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin" ,"ping", "-h", "localhost", "-u", "root", "-p$${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  scylladb:
    container_name: temporal-scylladb
    image: scylladb/scylla:6.2
    networks:
      - temporal-network
    ports:
      - "9042:9042"
    volumes:
      - scylla-data:/var/lib/scylla

  elasticsearch:
    container_name: temporal-elasticsearch
    image: elasticsearch:7.16.2
    environment:
      - cluster.routing.allocation.disk.threshold_enabled=true
      - cluster.routing.allocation.disk.watermark.low=512mb
      - cluster.routing.allocation.disk.watermark.high=256mb
      - cluster.routing.allocation.disk.watermark.flood_stage=128mb
      - discovery.type=single-node
      - ES_JAVA_OPTS=-Xms256m -Xmx256m
      - xpack.security.enabled=false
    networks:
      - temporal-network
    ports:
      - "9200:9200"
    volumes:
      - elasticsearch-data:/var/lib/elasticsearch/data

  temporal:
    container_name: temporal
    image: temporalio/auto-setup:1.26.2
    depends_on:
      - scylladb
      - elasticsearch
    environment:
      - DB=cassandra
      - CASSANDRA_SEEDS=scylladb
      - ENABLE_ES=true
      - ES_SEEDS=elasticsearch
      - ES_VERSION=v7
    networks:
      - temporal-network
    ports:
      - "7233:7233"

  temporal-admin-tools:
    container_name: temporal-admin-tools
    image: temporalio/admin-tools:1.26.2
    depends_on:
      - temporal
    environment:
      - TEMPORAL_ADDRESS=temporal:7233
      - TEMPORAL_CLI_ADDRESS=temporal:7233
    networks:
      - temporal-network
    stdin_open: true
    tty: true

  temporal-ui:
    container_name: temporal-ui
    image: temporalio/ui:2.31.2
    depends_on:
      - temporal
    environment:
      - TEMPORAL_ADDRESS=temporal:7233
      - TEMPORAL_CORS_ORIGINS=http://localhost:3000
    networks:
      - temporal-network
    ports:
      - "8081:8080"

networks:
  temporal-network:
    driver: bridge
    name: temporal-network

volumes:
  mysql_data:
  scylla-data:
  elasticsearch-data:
```

### Spring Boot Configuration

**TemporalConfiguration.java**:
```java
@Configuration
public class TemporalConfiguration {
    @Value("${temporal.namespace:ordering-system}")
    private String temporalNamespace;
    
    @Value("${temporal.address:localhost:7233}")
    private String temporalAddress;
    
    // Automatic namespace creation with error handling
    // WorkflowServiceStubs, WorkflowClient, and WorkerFactory beans
}
```

**Key Features**:
- Automatic namespace registration with retry logic
- 7-day workflow retention policy
- Connection to `localhost:7233` (configurable)
- Graceful handling of existing namespaces

## Workflow Implementation

### Order Processing Workflow

**State Machine**:
```
PENDING → VALIDATING → RESERVING_STOCK → PROCESSING_PAYMENT → CONFIRMED → COMPLETED
            ↓              ↓                ↓                ↓
         FAILED         FAILED          FAILED          CANCELLED
```

**OrderProcessingWorkflowImpl.java**:
```java
@WorkflowInterface
public interface OrderProcessingWorkflow {
    @WorkflowMethod
    OrderWorkflowResult processOrder(OrderWorkflowInput input);
    
    @QueryMethod
    OrderWorkflowState getOrderState();
    
    @SignalMethod
    void updateOrderStatus(String newStatus);
    
    @SignalMethod
    void cancelOrder();
}
```

### Activity Decomposition

The workflow delegates business logic to activities that integrate with MySQL:

#### 1. InventoryActivity
```java
@ActivityImpl(taskQueues = "ORDER_PROCESSING_TASK_QUEUE")
public class InventoryActivityImpl implements InventoryActivity {
    private final ProductService productService; // Injects Spring service
    
    @Override
    public void reserveStock(long orderId, List<OrderItemDetails> items) {
        // Uses ProductService which uses jOOQ to query MySQL
        // Updates product inventory in database
    }
}
```

#### 2. PaymentActivity
```java
@ActivityImpl(taskQueues = "ORDER_PROCESSING_TASK_QUEUE")
public class PaymentActivityImpl implements PaymentActivity {
    @Override
    public String processPayment(long orderId, PaymentDetails paymentDetails) {
        // Mock payment processing
        // In real implementation, would integrate with payment gateway
        // Returns transaction ID for tracking
    }
}
```

#### 3. OrderStatusActivity
```java
@ActivityImpl(taskQueues = "ORDER_PROCESSING_TASK_QUEUE")
public class OrderStatusActivityImpl implements OrderStatusActivity {
    private final OrderService orderService; // Injects Spring service
    
    @Override
    public void updateStatus(long orderId, OrderStatus newStatus) {
        // Uses OrderService to update order status in MySQL
        // Keeps database in sync with workflow state
    }
}
```

#### 4. NotificationActivity
```java
@ActivityImpl(taskQueues = "ORDER_PROCESSING_TASK_QUEUE")
public class NotificationActivityImpl implements NotificationActivity {
    @Override
    public void sendOrderConfirmation(long orderId, String customerEmail) {
        // Mock notification sending
        // In real implementation, would integrate with email service
    }
}
```

## MySQL Integration Details

### Data Access Layer

**jOOQ Integration**:
- Type-safe SQL queries generated from database schema
- Repository pattern with jOOQ implementation
- Spring transaction management

**OrderRepositoryMySQLImpl.java**:
```java
@Repository
public class OrderRepositoryMySQLImpl implements OrderRepository {
    private final DSLContext dsl; // jOOQ DSL context
    
    @Override
    public boolean updateTemporalWorkflowId(Long orderId, String workflowId) {
        int rowsUpdated = dsl.update(ORDERS)
            .set(ORDERS.TEMPORAL_WORKFLOW_ID, workflowId)
            .where(ORDERS.ID.eq(orderId))
            .execute();
        return rowsUpdated > 0;
    }
    
    @Override
    public boolean updateStatus(Long orderId, OrderStatus status) {
        int rowsUpdated = dsl.update(ORDERS)
            .set(ORDERS.STATUS, status.name())
            .set(ORDERS.UPDATED_AT, LocalDateTime.now())
            .where(ORDERS.ID.eq(orderId))
            .execute();
        return rowsUpdated > 0;
    }
}
```

### Service Layer Integration

**OrderServiceImpl.java**:
```java
@Service
@Transactional
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final ProductService productService;
    
    @Override
    public OrderRecord placeOrder(OrderRecord order, List<OrderItemRecord> orderItems) {
        // 1. Validate order and items
        validateOrder(order, orderItems);
        
        // 2. Check stock availability (MySQL query via ProductService)
        Map<Long, Boolean> stockAvailability = 
            productService.checkStockAvailabilityBatch(productQuantities);
        
        // 3. Create order in MySQL with PENDING status
        OrderRecord savedOrder = orderRepository.save(newOrder);
        
        // 4. Save order items and reserve stock
        for (OrderItemRecord item : orderItems) {
            orderItemRepository.save(newItem);
            productService.reserveStock(item.productId(), item.quantity());
        }
        
        // 5. TODO: Start Temporal workflow here
        // WorkflowClient.start(OrderProcessingWorkflow.class, workflowInput);
        
        return savedOrder;
    }
    
    @Override
    public boolean updateTemporalWorkflowId(Long orderId, String workflowId) {
        return orderRepository.updateTemporalWorkflowId(orderId, workflowId);
    }
}
```

## Integration Flow

### Current Flow (Incomplete)

1. **Order Placement**: REST → OrderController → OrderService
2. **Database Operations**: Order and items saved to MySQL
3. **Stock Reservation**: Product inventory updated in MySQL
4. **Missing Step**: Temporal workflow is not started

### Intended Complete Flow

1. **Order Placement**: REST → OrderController → OrderService
2. **Database Operations**: Order saved to MySQL with PENDING status
3. **Workflow Start**: Temporal workflow initiated with order ID
4. **Workflow Execution**:
   - Activities execute business logic via Spring services
   - Services use jOOQ to interact with MySQL
   - Order status updated in MySQL as workflow progresses
5. **Completion**: Final status persisted in both MySQL and Temporal

## Worker Configuration

**TemporalWorkerService.java**:
```java
@Service
public class TemporalWorkerService {
    private final WorkerFactoryWrapper workerFactory;
    private final InventoryActivity inventoryActivity;
    private final PaymentActivity paymentActivity;
    private final OrderStatusActivity orderStatusActivity;
    private final NotificationActivity notificationActivity;
    
    @PostConstruct
    public void startWorkers() {
        Worker orderProcessingWorker = 
            workerFactory.newWorker("ORDER_PROCESSING_TASK_QUEUE");
        
        // Register workflow implementation
        orderProcessingWorker.registerWorkflowImplementationTypes(
            OrderProcessingWorkflowImpl.class);
        
        // Register Spring-managed activity implementations
        orderProcessingWorker.registerActivitiesImplementations(
            inventoryActivity, paymentActivity, 
            orderStatusActivity, notificationActivity);
        
        workerFactory.start();
    }
}
```

## Error Handling & Compensation

### Retry Configuration
```java
private final ActivityOptions defaultActivityOptions =
    ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .setRetryOptions(
            RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setInitialInterval(Duration.ofSeconds(1))
                .build())
        .build();
```

### Compensation Activities
- `releaseStock()`: Reverses inventory reservations
- `refundPayment()`: Processes payment refunds
- Database rollback through Spring transactions

## Key Integration Points

1. **Spring Dependency Injection**: Activities are Spring components that inject services
2. **Transaction Management**: Spring `@Transactional` ensures data consistency
3. **jOOQ Integration**: Activities use services that perform type-safe SQL operations
4. **State Synchronization**: `OrderStatusActivity` keeps MySQL in sync with workflow state
5. **Workflow Linking**: `temporal_workflow_id` field connects database records to workflows

## Missing Implementation

**Critical Gap**: The `OrderServiceImpl.placeOrder()` method creates orders in MySQL but doesn't start the corresponding Temporal workflow. This needs to be added:

```java
// Missing in OrderServiceImpl.placeOrder():
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setWorkflowId("order-" + savedOrder.id())
    .setTaskQueue("ORDER_PROCESSING_TASK_QUEUE")
    .build();

OrderProcessingWorkflow workflow = workflowClient.newWorkflowStub(
    OrderProcessingWorkflow.class, options);

// Start workflow asynchronously
WorkflowClient.start(workflow::processOrder, workflowInput);

// Update order with workflow ID
updateTemporalWorkflowId(savedOrder.id(), "order-" + savedOrder.id());
```

This integration provides the foundation for reliable, observable, and fault-tolerant order processing with proper separation between persistent data (MySQL) and process orchestration (Temporal).