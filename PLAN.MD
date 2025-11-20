# PLAN: Multitenancy por tenant_id por fila (Traccar)

Este plan describe los cambios recomendados para convertir Traccar en multi‑tenant usando el enfoque de una sola base de datos compartida con `tenant_id` por fila. Cubre diseño, tareas por capa, orden de ejecución, criterios de aceptación, riesgos y rollback.

## 1. Objetivo y alcance

- Permitir que una instancia de Traccar sirva múltiples empresas (tenants) con aislamiento de datos por empresa.
- Afecta a backend (Java), esquema de BD (Liquibase), cachés, permisos, ingesta de dispositivos, reportes, notificaciones y UI (`traccar-web`).
- No cambia protocolos ni dispositivos, salvo que se opte por `uniqueId` por tenant (no recomendado inicialmente).

## 2. Enfoque de multitenencia

- Modelo: una BD compartida, con `tenant_id` en filas de entidades de negocio.
- Aislamiento: lógico, aplicado automáticamente en consultas/operaciones a través de `Storage/QueryBuilder` y validado por `PermissionsService`.

## 3. Decisiones clave (iniciales)

- `devices.uniqueId`: se mantiene globalmente único (simplifica ingesta y compatibilidad de protocolos).
- `positions`/`events`: Fase 1 sin `tenant_id` (filtrar por join a `devices.tenant_id`); Fase 2 opcional con `tenant_id` directo para optimización/Timescale.
- `users.login`/`users.email`: unicidad por tenant → índices únicos compuestos `(tenant_id, login)` y/o `(tenant_id, email)`.
- Roles: `SUPER_ADMIN` (global), `TENANT_ADMIN`, `MANAGER`, `USER`.
- Resolución de tenant en UI/API: por subdominio o selector en login; el `tenantId` viaja en JWT/session.

## 4. Impacto por capa

### 4.1 Esquema de BD (Liquibase) — carpeta `schema/`

- Nueva tabla `tenants(id, name, slug, status, created_at, ... )`.
- Añadir `tenant_id` con FK a `tenants(id)` en:
  - `users`, `devices`, `groups`, `geofences`, `drivers`, `calendars`, `attributes` (incl. computed), `notifications`, `saved_commands`, `maintenances`, `user_permissions`/`group_device`/tablas de enlace equivalentes.
- Índices por `tenant_id` y compuestos pertinentes (p. ej. `(tenant_id, name)`; `(tenant_id, groupId)` en enlaces).
- Unicidades por tenant donde aplique: `(tenant_id, login)`, `(tenant_id, name)`, etc.
- Fase 1: no tocar `positions/events`; consultas usarán join por `deviceId`.
- Fase 2 (optativa): añadir `tenant_id` a `positions/events` y crear índices compuestos `(tenant_id, deviceId, time)` (Timescale: `tenant_id` como dimensión espacial).

### 4.2 Modelo de dominio — `src/main/java/org/traccar/model/*`

- Agregar `tenantId` a modelos raíz y relacionados (p. ej. `User`, `Device`, `Group`, `Geofence`, ...). 
- Introducir interfaz marcador opcional `TenantScoped { long getTenantId(); void setTenantId(long); }` para aplicar políticas genéricas.

### 4.3 Capa de almacenamiento — `org.traccar.storage.*`

- Añadir un `TenantContext` (ThreadLocal o Reactive Context) que exponga el `tenantId` de la request actual.
- Decorar `Storage`/`QueryBuilder` para agregar automáticamente `WHERE tenant_id = :tenantId` en SELECT/UPDATE/DELETE sobre entidades `TenantScoped`.
- En INSERT, setear `tenant_id` con el contexto si el modelo lo soporta.

### 4.4 Permisos y seguridad — `org.traccar.api.security.PermissionsService`

- Validar que `user.tenantId == entity.tenantId` para toda operación (excepto `SUPER_ADMIN`).
- En creación/edición, forzar `entity.tenantId = currentTenantId`.
- Extender `checkPermission`, `checkEdit`, `checkUserUpdate` para incluir verificación de tenant.

### 4.5 Autenticación/API — `org.traccar.api.*`

- Resolución de tenant:
  - Subdominio o selector en login (guardar `tenantId` en JWT/Session).
  - Propagar `tenantId` al `TenantContext` en filtros/recursos.
- Endpoints reutilizan `PermissionsService` + filtros del `Storage` (mínimo cambio por endpoint).

### 4.6 Cachés y lookups — `org.traccar.database.DeviceLookupSer0vice`, `org.traccar.storage.MemoryStorage`

- Con `uniqueId` global, no cambian claves; asegurar que los conjuntos de permisos estén particionados por tenant cuando aplique.
- Si en el futuro se pasa a `uniqueId` por tenant, clave de caché → `(tenantId, uniqueId)`.

### 4.7 Ingesta de dispositivos — `org.traccar.session.DeviceSession` y decoders

- Sin cambios si `uniqueId` global. `DeviceSession` recupera por `uniqueId` y `Device` trae su `tenantId` de manera natural.
- Si se decide `uniqueId` por tenant, definir mecanismo para inferir tenant (host/puerto/SNI/token) y propagarlo hasta el lookup.

### 4.8 Reportes / jobs — `org.traccar.reports.*`, `org.traccar.schedule.*`

- Asegurar que los dispositivos origen estén filtrados por tenant.
- Consultas directas a `positions/events`: incluir join a `devices` (Fase 1) o filtro `tenant_id` directo (Fase 2).

### 4.9 Notificaciones / Mailer / WebSocket — `org.traccar.reports.common`, `org.traccar.web`

- Adjuntar `tenantId` en el contexto de entrega y filtrar emisiones por tenant.
- Configuraciones y plantillas por tenant (opcional).

### 4.10 UI (`traccar-web`)

- Login con subdominio o selector de empresa; guardar `tenantId` en token.
- Branding por tenant (opcional): logo, colores, nombre.

### 4.11 Observabilidad y seguridad operativa

- MDC/Logging con `tenantId`.
- Métricas por tenant (ingestas/min, errores, latencias).
- Rate limits por tenant y usuario.

## 5. Plan de implementación por fases

1) Preparación
- Revisar versión de esquema actual y dependencias de TimescaleDB. 
- Acordar política de `uniqueId` (global). 
- Seleccionar método de resolución de tenant (subdominio vs selector).

2) Esquema – Fase 1
- Crear tabla `tenants` (changeSet `mt-001`).
- Añadir `tenant_id` a tablas principales (changeSet `mt-002` por bloque lógico). 
- Backfill: asignar `tenant_id = TENANT_DEFAULT` a todas las filas existentes (changeSet `mt-00x-backfill`).
- Crear índices y unicidades compuestas (changeSet `mt-003`).

3) Modelo de dominio
- Añadir `tenantId` a `User`, `Device`, `Group`, etc. y la interfaz `TenantScoped`.

4) Seguridad y contexto
- Incluir `tenantId` en JWT/session y cargarlo en un `TenantContext` en filtros web.
- Extender `PermissionsService` para validar tenant en operaciones.

5) Capa de almacenamiento
- Decorar `Storage/QueryBuilder` para auto‑filtrar por `tenant_id` y forzar set en INSERT.

6) Cachés y permisos en memoria
- Particionar mapas de permisos por tenant en `MemoryStorage` cuando aplique.
- Validar `DeviceLookupService` (sin cambios con `uniqueId` global).

7) Barrido de API y servicios
- Verificar endpoints que construyen consultas manuales y asegurar filtro por tenant.

8) Reportes/Jobs
- Ajustar consultas a `positions/events` con join a `devices` por tenant.

9) UI
- Añadir selección/derivación de tenant en login y branding básico.

10) Pruebas
- Unitarias para `PermissionsService` y filtros de `Storage`.
- Integración: 
  - Aislamiento (usuario de A no ve datos de B).
  - Ingesta (dispositivo de A solo visible en A).
- Performance básica (listados por tenant, reportes con joins).

11) Despliegue
- Feature flag para activar validación por tenant.
- Migración gradual, monitoreo y rollback plan.

12) Optimización – Fase 2 (opcional)
- Añadir `tenant_id` a `positions/events` y crear índices para Timescale.
- Medir beneficios y riesgo de migración.

## 6. Rutas y archivos tocados (referencia)

- BD: `schema/changelog-*.xml` (nuevos changeSets `mt-*`).
- Modelos: `src/main/java/org/traccar/model/User.java`, `Device.java`, `Group.java`, demás entidades.
- Permisos: `src/main/java/org/traccar/api/security/PermissionsService.java`.
- Storage: `src/main/java/org/traccar/storage/Storage.java`, `DatabaseStorage.java`, `QueryBuilder.java`, decoradores.
- Cachés: `src/main/java/org/traccar/database/DeviceLookupService.java`, `src/main/java/org/traccar/storage/MemoryStorage.java`.
- API/Web: `src/main/java/org/traccar/api/*`, `src/main/java/org/traccar/web/*`.
- UI: `traccar-web/*`.

## 7. Criterios de aceptación

- Aislamiento: un usuario de un tenant no puede leer/editar objetos de otro tenant (endpoints, reportes, WebSocket).
- Ingesta: posiciones de un dispositivo se asocian y consultan dentro del tenant correcto.
- Permisos: `PermissionsService` rechaza accesos cross‑tenant y fuerza `tenantId` en altas/ediciones.
- Consultas: `Storage/QueryBuilder` aplica `tenant_id` automáticamente en entidades con soporte de tenant.
- Performance: listados y reportes mantienen tiempos aceptables con índices por `tenant_id`.

## 8. Riesgos y mitigaciones

- Fugas de datos por endpoints no cubiertos → Mitigar con barrido de API + tests de aislamiento.
- Degradación por joins con `devices` en `positions/events` → Índices adecuados; considerar Fase 2 con `tenant_id` directo si es necesario.
- Inconsistencias de `tenant_id` en INSERT → Forzar set en `Storage` e invariantes en `PermissionsService`.
- Complejidad de cachés → Añadir claves por tenant donde aplique y pruebas.

## 9. Rollback

- Mantener changeSets reversibles cuando sea viable (Liquibase `rollback`).
- Flags para desactivar validaciones por tenant si hay incidentes.
- Backups antes de migraciones y scripts de restauración.

## 10. Anexos

### 10.1 Lista inicial de tablas a modificar (indicativo)

- Principales: `users`, `devices`, `groups`, `geofences`, `drivers`, `calendars`, `computed_attributes`, `notifications`, `saved_commands`, `maintenances`.
- Enlaces: `user_device`, `group_device`, `user_group`, `device_geofence`, etc. (añadir `tenant_id` o validar por join que ambas puntas comparten tenant).
- Grandes (Fase 2): `positions`, `events` (Timescale Hypertables con partición temporal y espacial por `tenant_id`).

### 10.2 Esqueleto de changeSets (referencial)

```xml
<!-- mt-001: tabla tenants -->
<changeSet id="mt-001-tenants" author="junie">
  <createTable tableName="tenants">
    <column name="id" type="BIGINT" autoIncrement="true">
      <constraints primaryKey="true"/>
    </column>
    <column name="name" type="VARCHAR(255)"/>
    <column name="slug" type="VARCHAR(64)"/>
    <column name="status" type="SMALLINT" defaultValueNumeric="1"/>
    <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
  </createTable>
  <createIndex tableName="tenants" indexName="idx_tenants_slug">
    <column name="slug"/>
  </createIndex>
  <addUniqueConstraint tableName="tenants" columnNames="slug" constraintName="uk_tenants_slug"/>
.</changeSet>

<!-- mt-002: añadir tenant_id a users/devices/groups (repetir por tabla) -->
<changeSet id="mt-002-users-tenant" author="junie">
  <addColumn tableName="users">
    <column name="tenant_id" type="BIGINT" defaultValueNumeric="1"/>
  </addColumn>
  <addForeignKeyConstraint baseTableName="users" baseColumnNames="tenant_id"
                           referencedTableName="tenants" referencedColumnNames="id"/>
  <createIndex tableName="users" indexName="idx_users_tenant_id">
    <column name="tenant_id"/>
  </createIndex>
  <dropUniqueConstraint tableName="users" constraintName="uk_users_login"/>
  <addUniqueConstraint tableName="users" columnNames="tenant_id, login" constraintName="uk_users_tenant_login"/>
</changeSet>
```

---

Para ejecutar este plan, ver también `.junie/guidelines.md` y el documento `TASKS.md` (si corresponde) con el desglose operativo de tareas.
