# SmartDisplay MES EAP真机联调准备清单

Generated at: 2026-06-17

## 当前状态

### SECS/GEM影子协议驱动（V1.47）

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| 驱动代码 | ✅ READY | `SecsGemProtocolDriver.java` 已实现帧校验和归一化 |
| 驱动模式 | ⚠️ SHADOW | 仅帧校验，未配置真机握手端点 |
| 协议帧校验 | ✅ READY | 校验 `stream/function` 或 `secsMessage`，要求 `equipmentCode` |
| 消息归一化 | ✅ READY | S/F帧 → MES标准消息（messageType/equipmentCode/payload） |
| 必填字段 | ✅ READY | `secsMessage` (或 `stream+function`)、`equipmentCode` |
| 可选字段 | ✅ READY | `deviceId/ceid/rptId/systemBytes/transactionId` |
| 数据库网关记录 | ✅ READY | `equipment_gateway_connection` 表中 `GW-SECSGEM-SHADOW` |
| 健康检查 | ⚠️ WARN | 返回"SECS_GEM shadow protocol frame validation ready; real equipment handshake not configured" |

### OPC UA影子协议驱动（V1.47）

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| 驱动代码 | ✅ READY | `OpcUaProtocolDriver.java` 已实现帧校验和归一化 |
| 驱动模式 | ⚠️ SHADOW | 仅帧校验，未配置真机握手端点 |
| 协议帧校验 | ✅ READY | 校验 `nodeId`、`equipmentCode` |
| 消息归一化 | ✅ READY | OPC UA数据变化 → MES标准消息 |
| 必填字段 | ✅ READY | `nodeId`、`equipmentCode` |
| 可选字段 | ✅ READY | `browseName/operation/eventType/namespaceIndex/monitoredItemId/qualityCode/sourceTimestamp/serverTimestamp` |
| 数据库网关记录 | ✅ READY | `equipment_gateway_connection` 表中 `GW-OPCUA-SHADOW` |
| 健康检查 | ⚠️ WARN | 返回"OPC_UA shadow protocol frame validation ready; real equipment handshake not configured" |

## 真机联调前置条件

### 1. SECS/GEM设备侧准备

- [ ] **设备厂商确认SECS/GEM协议版本**（SEMI E5、SEMI E30）
- [ ] **设备侧开放HSMS连接端点**（IP地址、端口、Active/Passive模式）
- [ ] **设备侧配置DeviceID**（SEMI E5 中的 Device ID）
- [ ] **确认TLS/加密要求**（明文HSMS或TLS over TCP）
- [ ] **获取CEID清单**（Collection Event ID列表，哪些事件需要MES订阅）
- [ ] **获取RPTID清单**（Report ID列表，每个CEID关联的数据报告）
- [ ] **获取SVID清单**（Status Variable ID列表，设备状态变量映射）
- [ ] **获取S/F消息白名单**（哪些Stream/Function需要MES响应）

### 2. OPC UA设备侧准备

- [ ] **设备侧开放OPC UA端点**（`opc.tcp://` URI）
- [ ] **确认安全策略**（None/Basic128Rsa15/Basic256/Basic256Sha256）
- [ ] **确认认证模式**（Anonymous/Username/Certificate）
- [ ] **获取NamespaceIndex清单**（命名空间索引映射）
- [ ] **获取NodeId清单**（哪些节点需要MES监控，数据类型、采样周期）
- [ ] **获取BrowseName映射**（节点显示名称与MES参数映射）
- [ ] **确认订阅死带**（DeadBand，数值变化多少才触发上报）
- [ ] **确认采样周期**（PublishInterval，毫秒级推荐500-1000ms）

### 3. MES侧配置准备

#### 3.1 数据库配置（`equipment_gateway_connection`）

**SECS/GEM网关配置示例：**

```sql
UPDATE equipment_gateway_connection
SET 
    endpoint_uri = 'hsms://192.168.1.100:5000',  -- 真实设备IP和端口
    driver_mode = 'ACTIVE',                       -- SHADOW → ACTIVE
    tls_enabled = 0,                              -- 0=明文 1=TLS
    connection_timeout_ms = 10000,
    read_timeout_ms = 30000,
    driver_config_snapshot = json_build_object(
        'driverCode', 'secs-gem-driver',
        'driverMode', 'ACTIVE',
        'protocolType', 'SECS_GEM',
        'endpointUri', 'hsms://192.168.1.100:5000',
        'deviceId', '1',                          -- 设备Device ID
        'sessionMode', 'ACTIVE',                  -- ACTIVE=MES主动连接
        'tlsEnabled', false,
        'connectionTimeoutMs', 10000,
        'readTimeoutMs', 30000,
        'ceidWhitelist', ARRAY['1001', '1002', '1003'],  -- 订阅的CEID
        'protocolFrameValidation', true,
        'boundary', 'live equipment handshake'
    )::TEXT,
    status = 'CONNECTING',
    last_error = NULL
WHERE gateway_code = 'GW-SECSGEM-SHADOW';
```

**OPC UA网关配置示例：**

```sql
UPDATE equipment_gateway_connection
SET 
    endpoint_uri = 'opc.tcp://192.168.1.101:4840',  -- 真实OPC UA端点
    driver_mode = 'ACTIVE',                          -- SHADOW → ACTIVE
    tls_enabled = 0,                                 -- 0=不强制TLS 1=强制TLS
    connection_timeout_ms = 10000,
    read_timeout_ms = 30000,
    driver_config_snapshot = json_build_object(
        'driverCode', 'opc-ua-driver',
        'driverMode', 'ACTIVE',
        'protocolType', 'OPC_UA',
        'endpointUri', 'opc.tcp://192.168.1.101:4840',
        'securityPolicy', 'None',                    -- None/Basic256Sha256
        'authMode', 'Anonymous',                     -- Anonymous/Username/Certificate
        'tlsEnabled', false,
        'connectionTimeoutMs', 10000,
        'readTimeoutMs', 30000,
        'publishIntervalMs', 500,                    -- 订阅发布周期
        'nodeWhitelist', ARRAY['ns=2;s=Temperature', 'ns=2;s=Pressure'],  -- 监控的NodeId
        'protocolFrameValidation', true,
        'boundary', 'live equipment handshake'
    )::TEXT,
    status = 'CONNECTING',
    last_error = NULL
WHERE gateway_code = 'GW-OPCUA-SHADOW';
```

#### 3.2 网络配置

- [ ] **防火墙开放出站规则**（MES → 设备IP和端口）
- [ ] **防火墙开放入站规则**（设备 → MES，如果设备主动连接）
- [ ] **网络连通性验证**（telnet/nc验证端口可达）
- [ ] **DNS/Host文件配置**（如果endpoint_uri使用域名）

#### 3.3 驱动升级（从SHADOW到ACTIVE）

当前驱动是影子模式，真机联调需要：

1. **引入SECS/GEM客户端库**（如 `org.eclipse.ecp:secs4j` 或厂商SDK）
2. **引入OPC UA客户端库**（如 `org.eclipse.milo:sdk-client`）
3. **实现真实连接握手逻辑**：
   - SECS/GEM: HSMS连接建立、Select.req/rsp、S1F13/S1F14握手
   - OPC UA: Session创建、SecurityPolicy协商、Subscription创建
4. **实现消息收发逻辑**：
   - SECS/GEM: S6F11订阅CEID、S6F1接收事件、S2F33/S2F35/S2F37状态变量查询
   - OPC UA: CreateMonitoredItems订阅NodeId、PublishResponse接收数据变化
5. **实现心跳/超时/重连逻辑**

**注意：** 当前代码已预留帧校验和归一化逻辑，升级ACTIVE模式时保持接口不变，只需在`AbstractEapProtocolDriver`中实现`connect()`/`disconnect()`/`sendMessage()`/`onMessageReceived()`钩子。

## 验收标准

### SECS/GEM真机联调验收

| 项目 | 标准 |
| --- | --- |
| 连接握手 | HSMS连接建立成功，Select.req/rsp交换成功，`equipment_gateway_connection.status` 为 `CONNECTED` |
| 设备状态查询 | S1F3/S1F4获取设备状态成功，解析DeviceID正确 |
| CEID订阅 | S6F11订阅CEID成功，设备返回S6F12 ACK |
| 事件上报 | 设备触发CEID后，MES收到S6F11事件，`equipment_gateway_message` 表写入记录，`messageType` 归一化正确 |
| RPTID解析 | S6F11携带的RPTID数据正确解析到 `payload` 字段 |
| 健康检查 | `equipment_gateway_health_check` 表不再显示SHADOW警告，`status` 为 `HEALTHY` |
| 掉线重连 | 手动断开设备网络后，MES自动重连成功，`status` 从 `DISCONNECTED` 恢复到 `CONNECTED` |
| 毫秒级时延 | 从设备触发CEID到MES写入 `equipment_gateway_message` 表，P95时延 < 500ms |

### OPC UA真机联调验收

| 项目 | 标准 |
| --- | --- |
| 连接握手 | OPC UA Session创建成功，SecurityPolicy协商成功，`equipment_gateway_connection.status` 为 `CONNECTED` |
| 节点订阅 | CreateMonitoredItems订阅NodeId成功，设备返回SubscriptionId |
| 数据变化上报 | 节点数值变化后，MES收到PublishResponse，`equipment_gateway_message` 表写入记录，`messageType` 归一化正确 |
| NodeId解析 | PublishResponse携带的NodeId、Value、Timestamp、StatusCode正确解析到 `payload` 字段 |
| 健康检查 | `equipment_gateway_health_check` 表不再显示SHADOW警告，`status` 为 `HEALTHY` |
| 掉线重连 | 手动断开设备网络后，MES自动重连并重新订阅NodeId，`status` 恢复到 `CONNECTED` |
| 毫秒级采样 | PublishInterval=500ms时，数据变化上报周期稳定，无大幅抖动 |
| 死带过滤 | 配置DeadBand后，数值变化小于死带时不触发上报 |

## 后续工作

1. **引入SECS/GEM和OPC UA客户端库**（pom.xml添加依赖）
2. **实现ACTIVE模式连接握手逻辑**
3. **实现消息收发和订阅逻辑**
4. **实现心跳/超时/重连逻辑**
5. **补充真机联调单元测试**（Mock设备模拟器）
6. **补充真机联调E2E测试**（真实设备环境，或使用SECS/GEM Simulator + OPC UA Simulator）
7. **更新验收清单和落地进度文档**（真机联调通过后）

## 参考资料

- **SEMI E5**: SEMI Equipment Communications Standard 2 Message Content (SECS-II)
- **SEMI E30**: Generic Model for Communications and Control of Manufacturing Equipment (GEM)
- **SEMI E37**: High-Speed SECS Message Services (HSMS) Generic Services
- **OPC Foundation**: OPC Unified Architecture Specification Part 4: Services
- **Eclipse Milo**: Open-source OPC UA Java implementation (https://github.com/eclipse/milo)
- **SECS4J**: Java SECS/GEM implementation (https://github.com/serotonin/secs4j)
