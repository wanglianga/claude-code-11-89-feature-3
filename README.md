# 供水抢修调度服务（water-repair-service）

暴雨后的凌晨，值守人员从原始报修形成事故、核实隔离范围、派出合格班组并确认恢复。
本项目用 **Java 21 / Spring Boot 3.5 / PostgreSQL** 实现了一个真实可调用的后端服务，
覆盖：报修原文与归属判断、事故确认、图算法候选关阀组合与精确地址影响集合、
班组许可/当班窗口/车辆/工具校验、队伍与阀门操作许可的并发互斥、两种稀缺队伍调度路线、
五类现场回执的顺序约束、复压区间+校时容差核验、复压失败重新派工，以及压力复核推翻后的
范围/资源占用/通知全链路更正；并实现**相邻抢修同时停水时的临时供水调度**：
稳定住户标识去重、每户唯一取水点、诊所/安置点最低保障、按区预留与共享池两条配额路线、
车辆罐容与取水时段/无障碍接驳、合格水源余量与水质凭证有效期、装车出库/现场签收/
未开封退回复核的库存闭环，以及片区恢复后的通知撤回与未发出水量释放/转移。

> 项目类型：**后端 API 服务**。自带 OpenAPI 3 文档与 Swagger UI，无独立前端页面。

---

## 1. 一分钟启动

### 方式 A：Docker Compose（一键，推荐验收）

```bash
docker compose up --build
```

- 自动启动 PostgreSQL 18 与应用，应用监听 <http://localhost:8080>
- 宿主机 PostgreSQL 端口冲突时：`DB_HOST_PORT=55432 APP_PORT=18080 docker compose up --build`
- 容器内已配置阿里云 Maven 镜像；样例数据在首次启动时自动播种

打开：

- Swagger UI：<http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- 健康检查：<http://localhost:8080/api/health>

### 方式 B：本地开发启动（本工作区已验证）

环境：JDK 25（`/opt/jdk-25`，编译目标 21）、Maven 3.9（`/opt/maven`）、
PostgreSQL 18（本机 `127.0.0.1:5432`，库/用户/密码均为 `ccdev`）。

```bash
# 1) 确保 PostgreSQL 可连（容器自带实例）
PGPASSWORD=ccdev /opt/postgresql-18/bin/psql -h 127.0.0.1 -U ccdev -d ccdev -c 'select 1;'

# 2) 开发模式运行（首次会自动建表+播种）
JAVA_HOME=/opt/jdk-25 /opt/maven/bin/mvn -s settings.xml spring-boot:run

# 或打包后运行
JAVA_HOME=/opt/jdk-25 /opt/maven/bin/mvn -s settings.xml -DskipTests package
/opt/jdk-25/bin/java -jar target/water-repair-service.jar
```

如需使用自己的数据库，用环境变量覆盖：

```bash
DB_URL='jdbc:postgresql://HOST:5432/DBNAME' DB_USER=u DB_PASSWORD=p \
  /opt/jdk-25/bin/java -jar target/water-repair-service.jar
```

---

## 2. 测试账号

| 用户名 | 密码 | 角色 | 用途 |
| --- | --- | --- | --- |
| `dispatcher` | `Disp@12345` | DISPATCHER | 值班调度员，全部写操作（受理/确认/派工/复核/关单/reset） |
| `crewsole` | `Crew@12345` | CREW（绑定 `C_SOLE`） | 唯一资质装备齐备的持证维修班班长，可提交回执 |
| `scout` | `Scout@12345` | CREW（绑定 `C_SCOUT`） | 勘查一班 |
| `viewer` | `View@12345` | VIEWER | 只读角色，写接口返回 403 |

登录换取 token：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"dispatcher","password":"Disp@12345"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
echo "$TOKEN"
```

之后所有 `/api/**`（除 `/api/auth/login`、`/api/health`）都要带：

```
Authorization: Bearer <token>
```

在 Swagger UI 中点右上角 **Authorize**，粘贴 token 原文（不要带 `Bearer ` 前缀）即可调试。

---

## 3. 自动回放验证（推荐，纯 Python 标准库）

脚本会先登录、调用 `POST /api/admin/reset` 清库重播，然后真实发起并发 HTTP 请求，
逐步断言题目要求的全部场景。可重复执行。

```bash
# 终端 1：启动服务（见上）

# 终端 2：
python3 scripts/replay.py
# 或
bash scripts/verify.sh
# 远端/自定义地址：
BASE_URL=http://localhost:8080 python3 scripts/replay.py
```

预期结尾：

```
##############################################################################
# 回放断言结果：PASS=158  FAIL=0
# 🎉 全部场景断言通过：同段重拨 / 相邻双支管 / 并发互斥 / 复压重派 / 更正链 /
#           临时供水去重 / 双路线配额 / 恢复撤回 / 签收不返库 / 退回复核 / 凭证有效期
ALL_DONE: true
```

`scripts/replay.py` 覆盖并核对：

1. **同段重拨**：R1/R2 形成 I2 后，R3 热线重拨自动作为 `REDIAL` 来源挂入同一事故；
   事故数、派工数均不增加；重复追加来源幂等。
2. **相邻双支管**：相隔 <100m 的幸福路 8 号（A05→S2）与滨河巷 1 号（A06→S3）形成不同事故；
   路口地标与“幸福里 6 栋”同楼双地址都只给 `AMBIGUOUS [S2,S3]`，不自动合并；
   人工确认保留 v1 AUTO / v2 MANUAL 两级判断历史。
3. **精确影响集合**：单关 `V2 = A01..A05`；单关 `V1 = A01..A05 + A10..A13`（S1+S2，9 户）；
   `V3 = A06..A09`。I2 候选组合为 `[V2(5户), V1(9户)]`，I3 只有 `V3`，I1 只有 `V1`。
4. **资格门槛**：许可缺失（勘查班不能接维修单，422）、当班窗口外、
   车辆不可用、工具不可用都有逐项 `checks`。
5. **唯一持证班组两次并发派单**：两线程用 Barrier 同时请求 I2/I3 的维修派单，
   数据库行锁 + 时间窗重叠校验保证**恰好一个 200、一个 409 `CREW_ALREADY_BOOKED`**，
   409 细节给出占用方派工单/事故/窗口；取消后占用全部 `RELEASED` 且原因可查。
6. **两条调度路线**：
   - 路线 A（勘查先行，I3）：先派 `C_SCOUT`，04:25 到场确认 V3；
     跳序 `REPAIR` 被 `RECEIPT_ORDER_CONFLICT` 拒，勘查单提交 `ISOLATION` 被 422 拒；
     随后锁 `C_SOLE` 的 06:00-08:00 窗口被路线 B 的 I2 先占（409），
     最终改锁 09:30-11:30（与 I2 窗口 09:20 结束半开相接，不冲突）成功。
   - 路线 B（捆绑暂留，I2）：`holdOnly=true` 一次锁定
     `CREW C_SOLE + VALVE_PERMIT POP-V2 + VEHICLE VEH-1 + TOOL-*` 共 5 项 HELD 占用，
     派工单 `PROVISIONAL`，`issue` 后才 `DISPATCHED`。
7. **复压失败→重新派工**：I2 第一周期五回执齐全，`PR-S2-FAIL`（0.14MPa < S2 下限 0.22）
   核验 `FAILED`，关单 409 `REPRESSURE_NOT_PASSED`；`redispatch` 后
   旧派工单 5 项占用以 `REDISPATCH_AFTER_REPRESSURE_FAILURE` 释放，
   `cycleNo=2 / attemptNo=2 / parentAssignmentId` 成链；旧单再交回执报 `RECEIPT_WRONG_CYCLE`；
   新周期用 `PR-S2-OK` 通过并关单，新占用以 `INCIDENT_CLOSED` 释放。
8. **回执归属**：I3 的 `ARRIVAL` 永久归属勘查单/`C_SCOUT`，
   其余四条归属维修单/`C_SOLE`；每条回执都带 `assignmentId/assignmentCode/cycleNo`。
9. **压力复核更正链（I1）**：复核 `OVERTURNS` 把边界 S1/V1 改为 S2/V2：
   更正记录保存新旧边界/阀门/地址全集，`removed=[A10..A13]`、`added=[]`；
   占用侧释放 `POP-V1`（原因含 `PRESSURE_REVIEW_CORRECTION`）并新占 `POP-V2`；
   9 条原始通知全部 `SUPERSEDED` 且挂 `supersededByCorrectionId`，
   新发 5 条 `CORRECTION` + 4 条 `CORRECTION_CANCEL`（A10-A13），
   取消通知用 `replacesNotificationId` 链回旧通知——旧通知不删除、不伪装未发生。
10. **校时容差**：`POST /api/pressure/verify-repressure` 可分别判定
    “压力值超出支管区间”和“校时后采样时刻与复压时刻偏差超过容差”。
11. 终态：所有 `resource_occupancy` 均为 `RELEASED`，3 个事故均 `CLOSED`。
12. **住户去重与唯一取水点**：稳定标识 `residentCode` 全局唯一；甲区上报 180（140 独有 +
    40 与乙重复）、乙区上报 130（90 独有 + 同 40 重复），重叠户主要住址在甲，
    唯一服务归属为**甲 180、乙 90（合计 270，而非 310）**；每片区唯一取水点并保留
    取水时段、无障碍接驳与罐容；人均 2 升 + 诊所 400 / 安置点 600，得甲 760、乙 780、合计 1540。
13. **路线 A 按区预留（ZONE_RESERVED）**：派车前锁最低量（可再锁额外配额，护栏拒绝超额）；
    乙区装车前恢复 → 待发 780 撤回并自动复核回补、旧通知 `SUPERSEDED` + 新发 `WITHDRAW`；
    丙区新增 300 户（最低 600）→ 从乙可转移量转 600、释放 180 进共享池，
    连同原共享池 460，终余自由水 **640**；甲 760 已签收不回退。
14. **路线 B 共享池（SHARED_POOL）**：只锁各区最低量，其余 460 留共享池；乙恢复后 780 经
    `SHARED_RELEASE` 回池（不产生片区配额转移），丙区凭恢复回执从共享池滚动分 600，
    甲区 760 保障不变，终余同为 **640**；分配受“其他有效片区未覆盖最低量”护栏约束，
    先到片区无法耗尽共享量。
15. **库存与凭证闭环**：装车即锁水源写 `OUT` 台账并扣余量；现场签收计实交且**永不返库**；
    已开封退回 422 拒绝，未开封退回挂 `PENDING`，复核 `APPROVED` 且凭证仍有效才写 `RETURN`
    回补可再分配，`REJECTED` 不回补；水质凭证过期的 300 升既不计合格总量、也不能出库或借
    退回补足；重复复核 409 拒绝。

---

## 4. 手工 API 走查（核心端点）

所有写接口都需要调度员 token。完整 schema 见 Swagger UI。

```bash
B=http://localhost:8080
H="Authorization: Bearer $TOKEN"; J='Content-Type: application/json'

# 复位样例（回放脚本开头也会调用）
curl -s -X POST $B/api/admin/reset -H "$H" -H "$J" -d '{}'

# 管网与精确影响
curl -s $B/api/network/addresses -H "$H" | python3 -m json.tool
curl -s -X POST $B/api/network/impact -H "$H" -H "$J" -d '{"valveCodes":["V2"]}'
curl -s -X POST $B/api/network/candidate-isolations -H "$H" -H "$J" \
  -d '{"boundarySegmentCodes":["S2"]}'

# 受理报修（自动归属判断）
curl -s -X POST $B/api/reports -H "$H" -H "$J" -d '{
  "callerPhone":"13900000009","rawAddress":"幸福路3号",
  "description":"低压","receivedAt":"2026-11-26T03:05:00+08:00"}'

# 调度确认事故
curl -s -X POST $B/api/incidents -H "$H" -H "$J" -d '{
  "title":"S2 低压","reportIds":[1],"boundarySegmentCodes":["S2"]}'

# 候选方案 → 选 V2 → 通知 A01-A05
curl -s $B/api/incidents/1/candidate-isolations -H "$H"
curl -s -X POST $B/api/incidents/1/isolation-plans -H "$H" -H "$J" -d '{"valveCodes":["V2"]}'
curl -s -X POST $B/api/incidents/1/notify -H "$H"

# 资格预检 → 捆绑暂留派工 → 下达
curl -s -X POST $B/api/incidents/1/dispatch/eligibility -H "$H" -H "$J" -d '{
  "kind":"REPAIR","crewCode":"C_SOLE",
  "windowStart":"2026-11-26T05:00:00+08:00","windowEnd":"2026-11-26T07:00:00+08:00"}'
curl -s -X POST $B/api/incidents/1/dispatch/repair -H "$H" -H "$J" -d '{
  "crewCode":"C_SOLE","strategy":"RESERVE_BUNDLE","holdOnly":true,
  "windowStart":"2026-11-26T05:00:00+08:00","windowEnd":"2026-11-26T07:00:00+08:00"}'

# 按顺序提交回执（assignmentId 从上一步响应取）
curl -s -X POST $B/api/incidents/1/receipts -H "$H" -H "$J" -d '{
  "assignmentId":1,"type":"ARRIVAL","eventAt":"2026-11-26T05:05:00+08:00","note":"到场"}'

# 事故全状态聚合（核对来源/判断/方案/通知/更正/派工/占用/回执）
curl -s $B/api/incidents/1 -H "$H" | python3 -m json.tool
```

主要资源分组（Swagger Tag 与之一致）：

| 分组 | 代表端点 |
| --- | --- |
| 01 鉴权 | `POST /api/auth/login` |
| 02 管网图与影响 | `/api/network/*`、`POST /api/network/impact`、`/api/network/candidate-isolations` |
| 03 报修与归属 | `POST /api/reports`、`POST /api/reports/{id}/judgments` |
| 04 事故/方案/通知/复核 | `POST /api/incidents`、`/isolation-plans`、`/notify`、`/pressure-reviews`、`GET /{id}` |
| 05 派工与互斥 | `/dispatch/scout`、`/dispatch/repair`、`/redispatch`、`/api/assignments/{id}/cancel|complete|issue` |
| 06 回执与复压 | `POST /api/incidents/{id}/receipts`、`/close`、`POST /api/pressure/verify-repressure` |
| 07 临时供水 | `/api/supply/residents`、`/supply/plans`、`/zones/{code}/restore`、`/quotas/transfer`、`/shared-allocations`、`/deliveries`、`/deliveries/{id}/signoffs|returns`、`/returns/{id}/review` |
| 00 回放支持 | `POST /api/admin/reset` |

### 临时供水核心接口速查

```bash
B=http://localhost:8080
H="Authorization: Bearer $TOKEN"; J='Content-Type: application/json'

# 去重名册：街道上报/唯一服务/重叠人数与各片区最低量
curl -s $B/api/supply/residents -H "$H" | python3 -m json.tool

# 两条路线二选一建立方案（同一时刻只允许一个 ACTIVE 方案）
curl -s -X POST $B/api/supply/plans -H "$H" -H "$J" -d '{"mode":"ZONE_RESERVED"}'
curl -s -X POST $B/api/supply/plans -H "$H" -H "$J" -d '{"mode":"SHARED_POOL"}'

# 路线A：片区恢复 → 配额转移/释放；路线B：共享池滚动分配
curl -s -X POST $B/api/supply/zones/Z_YI/restore -H "$H" -H "$J" -d '{"note":"乙区恢复"}'
curl -s -X POST $B/api/supply/quotas/transfer -H "$H" -H "$J" \
  -d '{"fromZone":"Z_YI","toZone":"Z_BING","litres":600}'
curl -s -X POST $B/api/supply/shared-allocations -H "$H" -H "$J" \
  -d '{"zoneCode":"Z_BING","litres":600}'

# 装车（校验罐容/配额/凭证，扣水源余量）→ 现场签收 → 未开封退回复核
curl -s -X POST $B/api/supply/deliveries -H "$H" -H "$J" -d '{
  "zoneCode":"Z_JIA","vehicleCode":"WV-1","sourceCode":"WS-1","litres":760}'
curl -s -X POST $B/api/supply/deliveries/1/signoffs -H "$H" -H "$J" -d '{
  "litres":360,"residentCode":"R-JIA-ONLY-001","recipientType":"RESIDENT","signer":"网格员"}'
curl -s -X POST $B/api/supply/deliveries/1/returns -H "$H" -H "$J" \
  -d '{"litres":50,"sealed":true}'
curl -s -X POST $B/api/supply/returns/1/review -H "$H" -H "$J" \
  -d '{"approved":true,"note":"封条完好"}'
```

---

## 5. 领域模型与关键实现

### 5.1 随题样例管网（`src/main/resources/samples/network.json`）

```
NSRC ─ M0 ─ NT0 ─ F1 ─ NV1(V1) ─ F1D ─ NT1 ─ S1 ─ NE1   地址 A10-A13
                │                    └ F2 ─ NV2(V2) ─ S2 ─ NE2  地址 A01-A05
                └ F3 ─ NV3(V3) ─ S3 ─ NE3                      地址 A06-A09
```

- 节点：`SOURCE / TEE 三通 / VALVE 阀门 / END 末端`；管段连接两节点。
- 地址点唯一映射管段；`幸福里6栋`同时是 A04(S2)/A07(S3) 的别名，
  路口 `LM-XF-BH` 候选 `[S2,S3]`——用于证明“同楼/最近坐标 ≠ 同一事故”。
- 每段配置复压正常区间（如 S2 `[0.22, 0.45] MPa`）与校时容差（600s）。
- 压力读数带设备号、采样时刻 `sampledAt`、校时偏差 `clockOffsetSeconds`；
  约定 **校时后采样时刻 = sampledAt + clockOffsetSeconds**。

影响算法（`network/ImpactService.java`）：把关闭的阀门视为不可穿越节点，
从所有 SOURCE 做可达性遍历；一条管段两端都不可达即判为失压，其上地址全部受影响。
候选方案枚举全部阀门子集（≤4 阀组合），返回能隔离边界管段的“包含意义最小”组合。

### 5.2 归属与事故边界

- 每条报修永久保存原文（地址、描述、照片引用、接听时刻）。
- 自动归属按“最长别名命中 → 路口地标 → 坐标半径”的优先级产出
  `MATCHED / AMBIGUOUS / UNMATCHED` 判断；人工判断只追加新版本，不覆盖历史。
- 报修受理**不会**自动建事故；只有调度调用 `POST /api/incidents` 显式确认边界后才成案。
- 热线重拨带 `redialOfReportId/hotlineCallId`：若原线索已在事故中，
  新线索自动以 `REDIAL` 加入同一事故，不产生新事故或派工。

### 5.3 互斥与资格

- 资格逐项检查：班组 active、申请窗口落在当班时间（按 Asia/Shanghai）、
  所需资质（`Q_INSPECT/Q_REPAIR/Q_VALVE_STD/Q_VALVE_LARGE`，V1 要大阀许可）、
  绑定车辆 `available`、标配工具全部 `available`。
- 占用资源类型：`CREW / VALVE_PERMIT / VEHICLE / TOOL`。
- 预留时先对资源主行 `SELECT ... FOR UPDATE`，再用半开区间条件
  `existing.start < :end AND existing.end > :start AND status='HELD'` 查重叠。
  因此同一队伍（或同一阀门操作票）的两个并发派单在数据库层串行化，恰好一成一败；
  端点相接的窗口（如 09:20 结束 / 09:30 开始）不冲突。
- 释放不删行：占用行置 `RELEASED` 并写 `releaseReason/releasedAt`。

### 5.4 回执周期与更正链

- 顺序固定：`ARRIVAL → ISOLATION → REPAIR → FLUSH → REPRESSURE`，按 `(incident, cycleNo)` 成链。
- `ISOLATION` 及之后必须由 `REPAIR` 派工单提交；勘查单只能交 `ARRIVAL`。
- 复压必须引用压力读数，核验：压力值在该支管区间内、校时后采样时刻与回执时刻偏差 ≤ 容差、
  读数设备管段与当前边界一致；任一不过即 `FAILED`，`/close` 返回 409。
- `redispatch` 释放上一维修单占用、`cycleNo+1`、建立新 `Assignment(attemptNo,parent)`；
  旧周期回执与失败结论原样保留，旧单不能再写新周期。
- 压力复核 `OVERTURNS` 在一个事务内完成：旧方案 `CORRECTED` + 新方案版本、
  事故边界更新、地址集合 diff、阀门操作票“释放旧票/新占新票”（新票冲突则整体回滚）、
  旧通知 `SUPERSEDED` + 更正/取消通知链、`CorrectionRecord` 留全量快照。

### 5.5 临时供水调度（`supply/`）

- **稳定标识去重与唯一归属**：`ResidentRegistration.residentCode` 全局唯一，一户无论被多少
  街道重复上报都只按 `primaryZoneCode`（主要住址）归属一个片区、计数一次；`reportedZoneCodes`
  保留上报轨迹用于解释口径差。样例甲乙重叠 40 人主要住址在甲，故唯一服务为甲 180、乙 90。
- **读取主任务停水地址集合**：`GET /api/supply/outage-links` 汇总各事故当前 ACTIVE 隔离方案
  快照的 `affectedAddressCodes`（临时供水不自行估计范围），按片区 `segmentCode` 对齐到
  片区与唯一取水点；多个方案覆盖同一支管时按地址去重，不重复计量。
- **唯一取水点与服务要素**：每片区一个 `WaterPickupPoint`，携带取水时段、无障碍接驳、
  接驳罐容；片区最低量 = 唯一服务人数 × 人均量 + 诊所/安置点设施量。
- **两条配额路线**（`SupplyPlan.mode`，同时只允许一个 ACTIVE 方案）：
  - `ZONE_RESERVED`：建方案即锁最低量，可经 `extra-reservations` 再锁额外配额；恢复片区
    未发出的水成为 `transferableLitres`，经 `quotas/transfer` 转给新片区或
    `release-transferable` 释放进共享池，每次变化写 `QuotaLedgerEvent`。
  - `SHARED_POOL`：只锁各区最低量，其余合格水留共享池；恢复经 `SHARED_RELEASE` 回池，
    新需求经 `shared-allocations` 滚动分配。护栏 `uncoveredMinimumSum` 保证分配后其余有效
    片区最低量仍可满足，先到片区无法耗尽共享池。两路都不得突破仍有效片区的最低量。
- **库存闭环**：`WaterSource.availableLitres` 仅在装车 `OUT` 与复核通过 `RETURN` 时变动，
  全部留 `WaterSourceLedger`；装车校验车辆/取水点罐容、片区配额与水质凭证有效期；
  `WaterSignoff` 计实交且永不返库；`WaterReturn` 必须未开封且复核 APPROVED 才回补；
  凭证过期水源（`WS-EXP`）不计合格总量、不能出库、也不能借退回/库存总量补足。
- **恢复不抹历史**：片区恢复撤回无签收发的在途车（自动复核回补）、配额释放/转移、
  旧 `SupplyNotification` 置 `SUPERSEDED` 并新发 `WITHDRAW`（`replacesNotificationId` 链回），
  已签收交付与历史台账原样保留，已恢复区域不再显示待配送。

---

## 6. 本地开发与验证方式（实际执行记录）

以下验证均在本工作区容器内**实际执行通过**（日期 2026-11-25）：

| 验证 | 命令 | 实际结果 |
| --- | --- | --- |
| 编译 | `JAVA_HOME=/opt/jdk-25 /opt/maven/bin/mvn -s settings.xml compile` | BUILD SUCCESS |
| 单元测试 | `... mvn -s settings.xml test` | `ImpactServiceTest` 4 个 + `ResidentDedupServiceTest` 2 个，Tests run: 6, Failures: 0, Errors: 0 |
| 打包 | `... mvn -s settings.xml -DskipTests package` | 生成 `target/water-repair-service.jar`（约 57 MB fat jar） |
| 真实运行 | `/opt/jdk-25/bin/java -jar target/water-repair-service.jar` | 8080 启动成功，Hibernate 连 PostgreSQL 18.1 建表/播种（含 570 户供水登记），日志无 ERROR |
| 端到端回放 | `python3 scripts/replay.py` | `PASS=158 FAIL=0`，连续重复 3 次结果一致（含真实双线程并发派单与供水双路线重播） |
| 接口文档 | `GET /v3/api-docs`、`/swagger-ui/index.html` | 均 200，OpenAPI 含全部 `/api/**` 路径（其中 `/api/supply/**` 20 个端点） |

说明与限制（如实记录）：

- 当前容器**未安装 Docker**，因此 `docker compose up --build` 未能在本机实际执行；
  `Dockerfile`（多阶段 Maven 21 构建 + JRE 21 运行）与 `docker-compose.yml`
  （`postgres:18-alpine` + 健康检查 + 依赖编排）已按标准方式提供，需在有 Docker 的环境验收。
- 回放使用显式 ISO 时间（样例事故日 `2026-11-26`，时区 `+08:00`），不依赖系统时钟，可重复。
- 短信通道为 `SIMULATED_SMS`：通知落库并在接口/聚合详情中可查，不接真实短信网关。

---

## 7. 目录速览

```
pom.xml / settings.xml / Dockerfile / docker-compose.yml
scripts/replay.py                 # 158 断言的端到端回放（纯标准库，含临时供水双路线）
src/main/resources/application.yml
src/main/resources/samples/       # 随题提供的管网/班组/账号/报修/回执样本
src/main/java/com/waterrepair/
  auth/        账号、登录 token、角色拦截
  common/      统一异常、健康检查、首页跳转
  config/      Web/OpenAPI/播种/reset
  network/     节点/管段/阀门/地址/压力读数 + 图影响算法
  report/      报修原文 + 归属判断版本
  incident/    事故/来源/方案/通知/更正/压力复核
  crew/        班组/车辆/工具/阀门操作许可
  dispatch/    派工单 + 资源占用 + 行锁互斥 + 资格校验
  receipt/     五类顺序回执 + 复压核验
  supply/      临时供水：住户去重/片区取水点/水源凭证/双路线配额/派送签收退回/通知撤回
src/test/java/com/waterrepair/network/ImpactServiceTest.java
src/test/java/com/waterrepair/supply/ResidentDedupServiceTest.java
```
