# Guía de trabajo de Junie para este repositorio (Traccar)

Esta guía define cómo debe operar Junie (asistente autónomo) en este proyecto para mantener la calidad del código, la trazabilidad y la seguridad.

## 1. Contexto del proyecto

- Backend: Java (Gradle), paquete `org.traccar.*`.
- Persistencia: Capa `org.traccar.storage.*` (JDBC/QueryBuilder) y migraciones con Liquibase (`schema/*.xml`).
- BD recomendada: PostgreSQL/TimescaleDB (para `positions`/`events`).
- Frontend: `traccar-web` (Node/TypeScript).
- Configuración: `traccar.xml`, `debug.xml`.
- Infra: Docker (`Dockerfile.alpine`, `docker/compose/*`).

Rutas importantes:
- `src/main/java/org/traccar/...` (código de servidor)
- `src/test/java/org/traccar/...` (tests)
- `schema/` (migraciones Liquibase)
- `traccar-web/` (frontend)

## 2. Reglas de edición y estilo

- Cambiar solo lo necesario. Mantener estilo y convenciones del módulo (nombres, imports, formato, comentarios).
- No introducir dependencias pesadas si existe alternativa nativa.
- Evitar refactors masivos; preferir cambios locales seguros.
- Mantener compatibilidad hacia atrás siempre que sea posible (flags/configs).

## 3. Uso de herramientas del entorno

- Búsqueda: `search_project` con keywords cortas (no descripciones largas).
- Exploración: `get_file_structure` y `open` para revisar antes de editar.
- Edición: únicamente `apply_patch` para crear/editar archivos.
- Renombrados: exclusivamente `rename_element` para actualizar TODAS las referencias.
- Compilación/Pruebas: usar `run_test` o `build` cuando sea necesario para validar cambios (tests específicos mejor que builds completas).
- No combinar herramientas especiales con comandos de terminal en una misma llamada.

## 4. Consideraciones del entorno (Windows)

- Rutas con `\\` (backslash) y comandos PowerShell cuando corresponda.
- No asumir utilidades Linux en scripts.

## 5. Migraciones de base de datos (Liquibase)

- Toda alteración de esquema en `schema/*.xml` como nuevos `changeSet`.
- Reglas:
    - Idempotentes y con `author` identificable.
    - Crear índices y restricciones únicas/FK junto con las columnas.
    - Para tablas grandes (`positions`, `events`), planificar índices compuestos y considerar partición/hypertables en TimescaleDB.
    - Backfill en fases: defaults seguros, luego limpieza y constraints definitivas.

## 6. Seguridad y permisos

- No exponer credenciales ni secretos en código.
- Validaciones centralizadas en `org.traccar.api.security.PermissionsService`.
- Para multi‑tenant, validar `tenantId` en permisos y en almacenamiento (filtros automáticos).

## 7. Capa de almacenamiento y cachés

- Añadir lógica transversal (filtros por tenant, auditoría) en `org.traccar.storage` (p. ej. `QueryBuilder`, decoradores de `Storage`).
- Alinear cachés y lookups con `tenantId` cuando aplique (p. ej. `DeviceLookupService`).

## 8. Protocolos e ingesta de dispositivos

- Los decoders identifican por `uniqueId` (IMEI, etc.). Como el hardware no envía `tenant`, mantener `uniqueId` global simplifica la ingesta; si no, inferir tenant por host/puerto/token.

## 9. Frontend (`traccar-web`)

- Cambios de autenticación/tenant reflejados en login y token/contexto.
- Branding por empresa provisto por backend (evitar hardcode).

## 10. Pruebas, validación y despliegue

- Agregar pruebas unitarias/integración cerca de módulos tocados.
- Tests de aislamiento multi‑tenant: un usuario no accede a datos de otro tenant.
- Medir impacto en rendimiento (índices, joins `devices`/`positions`).
- Despliegues escalonados con métricas y plan de rollback.

## 11. Convenciones específicas para multi‑tenant

- Entidad `Tenant` y columna `tenant_id` en entidades principales.
- Unicidad por tenant donde aplique: `(tenant_id, login)`, `(tenant_id, name)`, etc.
- Filtro automático por `tenant_id` en consultas a través de `Storage/QueryBuilder`.
- Roles: `SUPER_ADMIN` (global), `TENANT_ADMIN`, `MANAGER`, `USER`.
- Preferir `devices.uniqueId` global salvo que la ingesta infiera `tenant` de forma fiable.

## 12. Checklist de calidad antes de PR

- [ ] Migraciones Liquibase creadas y probadas.
- [ ] Índices adecuados para nuevas consultas.
- [ ] Validaciones de permisos con `tenantId` implementadas.
- [ ] Cachés/lookups compatibles con `tenant` cuando aplique.
- [ ] Pruebas de aislamiento multi‑tenant agregadas.
- [ ] Documentación actualizada (`TASKS.md`, notas de despliegue).
