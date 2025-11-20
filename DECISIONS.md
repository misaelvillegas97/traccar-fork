# Decisiones Arquitectónicas: Multitenancy en Traccar

Documento que consolida las decisiones clave para la implementación de multitenancy por `tenant_id` por fila en Traccar.

---

## 1. Objetivo y Alcance (Tareas 1.1-1.3) ✓

### 1.1 Objetivos Validados

**Objetivo Principal:** Convertir Traccar en una plataforma multi-tenant que permita servir múltiples empresas/organizaciones desde una única instancia, con aislamiento completo de datos por empresa.

**Alcance Fase 1:**
- Backend (Java) con soporte de `tenant_id` en capa de modelo y almacenamiento
- Esquema de BD (Liquibase) con tabla `tenants` y columna `tenant_id` en tablas principales
- Capa de permisos extendida para validar accesos por tenant
- Cachés y lookups compatibles con tenant
- Ingesta de dispositivos sin cambios (mantener `uniqueId` global)
- API REST con propagación de `tenantId` vía JWT/session
- UI básica con selección de tenant en login

**Fuera de Alcance Fase 1:**
- No se añade `tenant_id` a `positions` ni `events` (se usa join a `devices`)
- No se modifica protocolo de dispositivos ni formato de ingesta
- No se implementa branding avanzado por tenant (opcional para Fase 2)

### 1.2 Documentación de Alcance

Este alcance está documentado en:
- `PLAN.MD`: Estrategia completa y desglose por capas
- `TASKS.md`: Tareas operativas con checkboxes de seguimiento
- Este archivo (`DECISIONS.md`): Decisiones clave consolidadas

### 1.3 Criterios de Éxito y Métricas

**Criterios de Aceptación:**
1. **Aislamiento de datos:** Usuario del tenant A no puede leer/escribir datos del tenant B
2. **Integridad referencial:** Todas las entidades relacionadas comparten el mismo `tenant_id`
3. **Performance:** Listados y reportes mantienen tiempos aceptables (<10% degradación)
4. **Ingesta correcta:** Dispositivos envían datos que se asocian al tenant correcto
5. **Tests de aislamiento:** Batería de tests que verifican no-acceso cross-tenant

**Métricas de Éxito:**
- **Funcional:** 100% de endpoints validan tenant; 0 fugas de datos en tests
- **Performance:** p95 de latencia de API <200ms; queries con índices por `tenant_id`
- **Operacional:** Migraciones aplicadas sin downtime; rollback probado y funcional
- **Seguridad:** Auditoría de accesos por tenant en logs; alertas de intentos cross-tenant

---

## 2. Enfoque de Multitenencia (Tareas 2.1-2.3) ✓

### 2.1 Modelo Ratificado: BD Compartida con tenant_id por Fila

**Modelo Elegido:** Shared Database, Shared Schema con discriminador `tenant_id`

**Ventajas:**
- Eficiencia de recursos (una sola BD, un solo esquema)
- Simplicidad operacional (un deploy, un conjunto de migraciones)
- Mantenimiento simplificado (un código para todos los tenants)

**Desventajas Mitigadas:**
- Riesgo de fugas de datos → Mitigado con filtros automáticos en `Storage/QueryBuilder` + validaciones en `PermissionsService`
- Noisy neighbor → Mitigado con índices adecuados, rate limits por tenant, y monitoreo
- Escalabilidad → TimescaleDB para `positions`/`events`; sharding por tenant en Fase 3 si necesario

### 2.2 Invariantes de Seguridad

**Invariante 1: Aislamiento por Tenant**
- Ninguna consulta SELECT/UPDATE/DELETE puede retornar/modificar filas de otro tenant
- Implementado en `QueryBuilder` con `WHERE tenant_id = :currentTenantId`
- Validado en `PermissionsService` antes de operaciones

**Invariante 2: Super Admin Global**
- El rol `SUPER_ADMIN` puede operar sobre cualquier tenant (bypass de filtros)
- Usado solo para administración de plataforma, no operaciones de negocio

**Invariante 3: Consistencia de tenant_id**
- Todos los INSERT fuerzan `tenant_id` del contexto actual
- No se permite cambiar `tenant_id` de una entidad tras creación (inmutable)

**Invariante 4: Propagación de Contexto**
- Toda request HTTP carga `tenantId` al `TenantContext` (ThreadLocal)
- El contexto se limpia al finalizar la request (finally block)

**Invariante 5: Unicidades por Tenant**
- Logins, emails, nombres de dispositivos/grupos son únicos dentro del tenant
- Índices únicos compuestos: `(tenant_id, login)`, `(tenant_id, name)`, etc.

### 2.3 Feature Flag de Validación por Tenant

**Nombre del Flag:** `multitenancy.enabled`

**Valores:**
- `false` (default en Fase 1): Validaciones por tenant desactivadas, comportamiento legacy
- `true` (activar post-migración): Validaciones activas, filtros automáticos aplicados

**Ubicación:** `traccar.xml` (configuración principal)

```xml
<entry key='multitenancy.enabled'>false</entry>
```

**Uso:**
- En `PermissionsService`: `if (config.getBoolean("multitenancy.enabled")) { validateTenant(...); }`
- En `QueryBuilder`: aplicar filtro `tenant_id` solo si flag activo
- En filtros web: cargar `TenantContext` solo si flag activo

**Plan de Activación:**
1. Desplegar código con flag OFF
2. Ejecutar migraciones de esquema
3. Activar flag en entorno staging y ejecutar tests
4. Activar flag en producción en canary (10% tráfico)
5. Monitorear métricas y logs 24h
6. Expandir a 50% y luego 100%
7. Rollback: apagar flag sin redesplegar código

---

## 3. Decisiones Clave (Tareas 3.1-3.6) ✓

### 3.1 Confirmación: devices.uniqueId Global

**Decisión:** Mantener `devices.uniqueId` globalmente único (sin `tenant_id` en la unicidad)

**Razones:**
1. **Simplicidad de ingesta:** Los dispositivos GPS envían IMEI/identificador sin conocer el tenant
2. **Compatibilidad de protocolos:** No requiere cambios en decoders existentes
3. **Operación sin downtime:** Un dispositivo puede cambiar de tenant (migración de cliente) sin reconfigurar hardware
4. **Realidad física:** Un IMEI es globalmente único en el mundo real

**Implicaciones:**
- Lookup de dispositivo por `uniqueId` no requiere `tenant_id`
- Tras lookup, el `Device` trae su `tenant_id` y ese se usa para validar accesos
- No hay riesgo de colisión de identificadores entre tenants

**Índice:**
```sql
CREATE UNIQUE INDEX uk_devices_uniqueid ON devices(uniqueId);
```

### 3.2 Estrategia Fase 1 para positions/events: Join a devices

**Decisión:** NO añadir `tenant_id` a `positions` ni `events` en Fase 1

**Razones:**
1. `positions` y `events` son tablas masivas (millones/miles de millones de filas)
2. Añadir columna y backfill es costoso en tiempo y bloqueos
3. El join `positions → devices` ya existe y está indexado

**Estrategia Fase 1:**
- Consultas filtran por tenant vía:
  ```sql
  SELECT p.* FROM positions p
  JOIN devices d ON p.deviceId = d.id
  WHERE d.tenant_id = :tenantId AND p.deviceTime BETWEEN ...
  ```
- Índice existente en `positions(deviceId, deviceTime)` es suficiente
- Performance aceptable con TimescaleDB hypertables particionadas por tiempo

**Fase 2 (Opcional - Evaluación Futura):**
- Si análisis de performance muestra degradación >10% en queries complejas
- Añadir `tenant_id` a `positions` y crear índice compuesto `(tenant_id, deviceId, deviceTime)`
- Backfill en lotes off-peak
- Habilitar partición espacial por `tenant_id` en TimescaleDB

### 3.3 Unicidades por Tenant

**Decisión:** Implementar unicidades compuestas por tenant donde aplique

**Unicidades Definidas:**

| Tabla | Columnas Únicas | Constraint |
|-------|----------------|------------|
| `users` | `(tenant_id, login)` | `uk_users_tenant_login` |
| `users` | `(tenant_id, email)` | `uk_users_tenant_email` (si email es obligatorio) |
| `devices` | `(uniqueId)` | `uk_devices_uniqueid` (GLOBAL) |
| `devices` | `(tenant_id, name)` | `uk_devices_tenant_name` (opcional, nombre amigable) |
| `groups` | `(tenant_id, name)` | `uk_groups_tenant_name` |
| `geofences` | `(tenant_id, name)` | `uk_geofences_tenant_name` |
| `drivers` | `(tenant_id, uniqueId)` | `uk_drivers_tenant_uniqueid` |
| `calendars` | `(tenant_id, name)` | `uk_calendars_tenant_name` |
| `notifications` | `(tenant_id, type, ...)` | Revisar lógica actual |
| `tenants` | `(slug)` | `uk_tenants_slug` (GLOBAL, para subdominios) |

**Estrategia de Migración:**
1. Drop constraint global existente (si existe): `ALTER TABLE users DROP CONSTRAINT uk_users_login;`
2. Add constraint compuesta: `ALTER TABLE users ADD CONSTRAINT uk_users_tenant_login UNIQUE (tenant_id, login);`

### 3.4 Roles y Capacidades Formalizadas

**Decisión:** Definir 4 roles jerárquicos para multi-tenant

#### Roles Definidos:

**1. SUPER_ADMIN (Plataforma)**
- **Alcance:** Toda la plataforma, todos los tenants
- **Capacidades:**
  - Crear/editar/eliminar tenants
  - Ver y operar sobre datos de cualquier tenant (bypass filtros)
  - Gestionar usuarios globales y administradores de tenant
  - Configuración de la instancia (traccar.xml)
- **Implementación:** `user.administrator == true && user.tenantId == null` (o tenantId especial = 0)
- **Restricción:** No expuesto en UI normal; acceso por panel admin separado

**2. TENANT_ADMIN (Administrador de Empresa)**
- **Alcance:** Su tenant únicamente
- **Capacidades:**
  - Gestionar usuarios de su tenant (crear/editar/eliminar)
  - Gestionar dispositivos, grupos, geofences de su tenant
  - Ver reportes y configurar notificaciones de su tenant
  - Configurar branding (logo, colores) si habilitado
- **Implementación:** `user.administrator == true && user.tenantId != null`
- **Restricción:** No puede cambiar su propio `tenantId` ni acceder a otros tenants

**3. MANAGER (Gerente/Supervisor)**
- **Alcance:** Su tenant, con límite de usuarios gestionables
- **Capacidades:**
  - Crear/editar usuarios limitados (`user.userLimit > 0`)
  - Ver todos los dispositivos del tenant
  - Crear/asignar grupos y permisos dentro del tenant
  - Generar reportes para dispositivos permitidos
- **Implementación:** `user.getManager() == true` (lógica existente: `userLimit != 0`)
- **Restricción:** No puede eliminar usuarios ni cambiar configuraciones de tenant

**4. USER (Usuario Regular)**
- **Alcance:** Dispositivos asignados específicamente
- **Capacidades:**
  - Ver dispositivos asignados a él (tabla `user_device`)
  - Ver posiciones y reportes de sus dispositivos
  - Recibir notificaciones configuradas
  - Readonly según flag `user.readonly`
- **Implementación:** `user.administrator == false && user.userLimit == 0`
- **Restricción:** No puede gestionar otros usuarios ni ver dispositivos no asignados

#### Matriz de Permisos:

| Operación | SUPER_ADMIN | TENANT_ADMIN | MANAGER | USER |
|-----------|-------------|--------------|---------|------|
| Ver tenants | ✓ Todos | ✗ | ✗ | ✗ |
| Crear tenant | ✓ | ✗ | ✗ | ✗ |
| Gestionar usuarios del tenant | ✓ | ✓ | ✓ (limitado) | ✗ |
| Ver todos dispositivos del tenant | ✓ | ✓ | ✓ | ✗ (solo asignados) |
| Crear dispositivos | ✓ | ✓ | ✓ | ✗ |
| Ver dispositivos de otro tenant | ✓ | ✗ | ✗ | ✗ |
| Generar reportes | ✓ | ✓ | ✓ | ✓ (solo sus dispositivos) |

### 3.5 Método de Resolución de Tenant

**Decisión:** Híbrido - Subdominio (preferido) + Selector (fallback)

#### Opción Primaria: Subdominio
- **Formato:** `{tenant-slug}.traccar.example.com`
- **Ventajas:**
  - Aislamiento visual claro
  - Soporte de branding por tenant (SSL wildcard)
  - Puede mapear a diferentes instancias en el futuro (sharding)
- **Implementación:**
  - Extraer `tenant-slug` del header `Host` en filtro web
  - Lookup de `tenantId` por `tenants.slug`
  - Cargar en `TenantContext`
- **Configuración:**
  ```xml
  <entry key='multitenancy.resolver'>subdomain</entry>
  <entry key='multitenancy.domain'>traccar.example.com</entry>
  ```

#### Opción Secundaria: Selector en Login
- **UI:** Dropdown o campo de texto "Empresa/Tenant" en pantalla de login
- **Ventajas:**
  - Funciona sin configuración DNS
  - Útil para development y testing
  - Permite a SUPER_ADMIN cambiar de tenant sin relogin
- **Implementación:**
  - Campo `tenantSlug` en request de login
  - Lookup de `tenantId` y validar que usuario pertenece a ese tenant
  - Incluir `tenantId` en JWT
- **Configuración:**
  ```xml
  <entry key='multitenancy.resolver'>selector</entry>
  ```

#### Opción Terciaria: Header (para APIs y testing)
- **Header:** `X-Tenant-ID` o `X-Tenant-Slug`
- **Uso:** APIs externas, webhooks, testing automatizado
- **Seguridad:** Validar que el token JWT también incluye ese `tenantId` (no permitir override arbitrario)

**Decisión Final:** Empezar con **Selector en Login** para Fase 1 (más simple), preparar código para soportar **Subdominio** en Fase 2 (recomendado para producción).

### 3.6 Estrategia de Branding por Tenant

**Decisión:** Branding básico opcional en Fase 1, avanzado en Fase 2

#### Fase 1 (Básico - Opcional):
**Campos en tabla `tenants`:**
- `name` (VARCHAR 255): Nombre de la empresa/organización
- `logo_url` (VARCHAR 512): URL del logo (almacenado en S3/CDN externo)
- `primary_color` (VARCHAR 7): Color primario HEX (ej: #3B82F6)

**Implementación UI:**
- Endpoint `/api/tenant/branding` retorna JSON con campos anteriores
- Frontend carga en boot y aplica a header/login
- Sin rebundling ni temas complejos

**Ejemplo JSON:**
```json
{
  "name": "Empresa ACME S.A.",
  "logoUrl": "https://cdn.example.com/tenants/acme/logo.png",
  "primaryColor": "#FF5733"
}
```

#### Fase 2 (Avanzado - Futuro):
- Múltiples colores (primario, secundario, fondo)
- Fuentes personalizadas
- Favicon por tenant
- Textos/labels personalizables (i18n por tenant)
- Email templates por tenant
- Dominio propio por tenant (white-label completo)

**Decisión Final:** Implementar solo campos `name` y `logo_url` en tabla `tenants` en Fase 1; `primary_color` y UI si hay tiempo. Fase 2 evaluar demanda real de clientes.

---

## 4. Próximos Pasos

Con las tareas 1-3 completadas, las siguientes etapas son:

1. **Tarea 4.1:** Crear migraciones Liquibase (tabla `tenants`, columnas `tenant_id`)
2. **Tarea 4.2:** Añadir `tenantId` a modelos de dominio (`User`, `Device`, etc.)
3. **Tarea 4.3:** Implementar `TenantContext` y filtros automáticos en `Storage`
4. **Tarea 4.4:** Extender `PermissionsService` con validaciones de tenant
5. **Tarea 4.5:** Propagación de `tenantId` en JWT/session

Ver `TASKS.md` para detalle completo de subtareas.

---

## 5. Riesgos Identificados y Planes de Mitigación

### Riesgo 1: Fugas de Datos Cross-Tenant
**Probabilidad:** Media | **Impacto:** Crítico
**Mitigación:**
- Tests automatizados de aislamiento (crear 2 tenants, verificar no-acceso)
- Auditoría de código buscando consultas SQL raw sin filtro `tenant_id`
- Logs de accesos sospechosos (usuario intenta acceder a `tenantId` diferente)

### Riesgo 2: Degradación de Performance
**Probabilidad:** Media | **Impacto:** Alto
**Mitigación:**
- Crear índices adecuados por `tenant_id` en todas las tablas
- Benchmark antes/después de activar filtros
- Considerar Fase 2 con `tenant_id` en `positions` si degradación >10%

### Riesgo 3: Complejidad de Rollback
**Probabilidad:** Baja | **Impacto:** Alto
**Mitigación:**
- Feature flag permite desactivar validaciones sin rollback de código
- Liquibase rollback scripts para deshacer migraciones
- Backups completos pre-migración
- Ensayo de rollback en staging

### Riesgo 4: Migración de Datos Existentes
**Probabilidad:** Alta | **Impacto:** Medio
**Mitigación:**
- Crear tenant default (id=1, slug='default')
- Backfill automático: `UPDATE users SET tenant_id = 1 WHERE tenant_id IS NULL;`
- Validar integridad referencial post-backfill
- No permitir `tenant_id NULL` tras migración (constraint NOT NULL)

---

## 6. Checklist de Revisión de Decisiones

- [x] 1.1 Objetivos y alcance validados con stakeholders
- [x] 1.2 Alcance documentado en PLAN.MD y DECISIONS.md
- [x] 1.3 Criterios de éxito y métricas definidos
- [x] 2.1 Modelo de BD compartida ratificado
- [x] 2.2 Invariantes de seguridad documentados
- [x] 2.3 Feature flag definido y estrategia de activación clara
- [x] 3.1 devices.uniqueId global confirmado
- [x] 3.2 Estrategia Fase 1 para positions/events definida (join)
- [x] 3.3 Unicidades por tenant especificadas por tabla
- [x] 3.4 Roles y capacidades formalizados (4 niveles)
- [x] 3.5 Método de resolución elegido (selector → subdominio)
- [x] 3.6 Estrategia de branding definida (básico Fase 1)

**Estado:** Tareas 1, 2 y 3 COMPLETADAS ✓

**Fecha:** 2025-11-20

**Autor:** Junie (Asistente Autónomo)
