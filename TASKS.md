TASKS: Implementar multitenancy por tenant_id por fila (Traccar)

Este documento desglosa PLAN.MD en tareas accionables numeradas por punto (1.1, 1.2, ... 12.x). Mantener trazabilidad vía issues/PRs.

1. Objetivo y alcance
- [x] 1.1 Validar objetivos y alcance Fase 1 con stakeholders.
- [x] 1.2 Documentar alcance definitivo en README/PLAN y comunicar.
- [x] 1.3 Definir criterios de éxito y métricas.

2. Enfoque de multitenencia
- [x] 2.1 Ratificar modelo: BD compartida con tenant_id por fila.
- [x] 2.2 Documentar invariantes (no acceso cross-tenant; SUPER_ADMIN global).
- [x] 2.3 Crear feature flag de validación por tenant.

3. Decisiones clave
- [x] 3.1 Confirmar devices.uniqueId global.
- [x] 3.2 Fijar estrategia Fase 1 para positions/events con join a devices.
- [x] 3.3 Definir unicidades por tenant para users y nombres relevantes.
- [x] 3.4 Formalizar roles y capacidades.
- [x] 3.5 Elegir método de resolución de tenant (subdominio vs selector).
- [x] 3.6 Estrategia de branding por tenant (opcional).

4. Impacto por capa
4.1 Esquema (Liquibase)
- [ ] 4.1.1 Crear mt-001-tenants.
- [ ] 4.1.2 Crear mt-002-* para tenant_id en tablas principales.
- [ ] 4.1.3 Crear changeSets para tablas de enlace.
- [ ] 4.1.4 Backfill a TENANT_DEFAULT.
- [ ] 4.1.5 Unicidades compuestas por tenant.
- [ ] 4.1.6 Índices por tenant_id y compuestos.
- [ ] 4.1.7 Scripts de rollback mt-*.
- [ ] 4.1.8 Probar migraciones en staging.

4.2 Modelo de dominio
- [ ] 4.2.1 Añadir tenantId a modelos raíz.
- [ ] 4.2.2 Crear interfaz TenantScoped y aplicarla.
- [ ] 4.2.3 Ajustar constructores/getters/hashCode/JSON.
- [ ] 4.2.4 Actualizar DTOs/mappers.

4.3 Almacenamiento
- [ ] 4.3.1 Implementar TenantContext (set/get/clear).
- [ ] 4.3.2 Decorar QueryBuilder para WHERE tenant_id.
- [ ] 4.3.3 Auto set tenant_id en INSERT.
- [ ] 4.3.4 Unit tests de filtros y bypass SUPER_ADMIN.
- [ ] 4.3.5 Revisar DatabaseStorage/MemoryStorage y consultas raw.

4.4 Permisos
- [ ] 4.4.1 Validar user.tenantId == entity.tenantId.
- [ ] 4.4.2 Forzar entity.tenantId = currentTenantId en altas/ediciones.
- [ ] 4.4.3 Unit tests PermissionsService.

4.5 Autenticación/API
- [ ] 4.5.1 Incluir tenantId en JWT/session.
- [ ] 4.5.2 Filtro web que cargue TenantContext por request.
- [ ] 4.5.3 Propagar tenantId a BaseResource y servicios.
- [ ] 4.5.4 Pruebas de regresion de endpoints multi-tenant.

4.6 Cachés y lookups
- [ ] 4.6.1 Validar DeviceLookupService (uniqueId global).
- [ ] 4.6.2 Particionar MemoryStorage por tenant cuando aplique.
- [ ] 4.6.3 Tests de caché anti fugas cross-tenant.

4.7 Ingesta
- [ ] 4.7.1 Confirmar que decoders/DeviceSession no cambian.
- [ ] 4.7.2 Pruebas de ingesta aisladas por tenant.

4.8 Reportes/jobs
- [ ] 4.8.1 Auditar queries a positions/events y agregar join por tenant.
- [ ] 4.8.2 Tests de aislamiento y performance en reportes.

4.9 Notificaciones/WebSocket
- [ ] 4.9.1 Adjuntar tenantId al contexto y filtrar emisiones.
- [ ] 4.9.2 Tests end-to-end multi-tenant.

4.10 UI
- [ ] 4.10.1 Implementar derivacion o selector de tenant en login.
- [ ] 4.10.2 Incluir tenantId en token/session y consumir en API.
- [ ] 4.10.3 Branding básico por tenant (opcional).
- [ ] 4.10.4 Tests de UI multi-tenant.

4.11 Observabilidad
- [ ] 4.11.1 Incluir tenantId en logs (MDC) y trazas.
- [ ] 4.11.2 Métricas por tenant (Prometheus).
- [ ] 4.11.3 Rate limits por tenant/usuario.
- [ ] 4.11.4 Dashboards y alertas por tenant.

5. Fases de implementación
- [ ] 5.1 Preparación: cerrar decisiones, validar Timescale, feature flag.
- [ ] 5.2 Esquema Fase 1: mt-001, mt-002-*, mt-003; migrar dev/staging; backfill.
- [ ] 5.3 Modelo: aplicar cambios e interfaz TenantScoped.
- [ ] 5.4 Seguridad/contexto: JWT + filtro TenantContext; extender permisos.
- [ ] 5.5 Storage: filtro automático e inserts; tests.
- [ ] 5.6 Cachés: particionar y testear.
- [ ] 5.7 Barrido API/servicios: revisar consultas manuales.
- [ ] 5.8 Reportes/Jobs: ajustar queries y tests.
- [ ] 5.9 UI: login multi-tenant y branding básico.
- [ ] 5.10 Pruebas: unitarias, integracion, performance.
- [ ] 5.11 Despliegue: activar flag en canary y monitorear; rollback listo.
- [ ] 5.12 Optimizacion Fase 2: tenant_id en positions/events si aporta valor.

6. Rutas y archivos
- [ ] 6.1 Liquibase: schema/changelog-*.xml con mt-* y orden maestro.
- [ ] 6.2 Modelos: src/main/java/org/traccar/model/*.java.
- [ ] 6.3 Permisos: src/main/java/org/traccar/api/security/PermissionsService.java.
- [ ] 6.4 Storage: src/main/java/org/traccar/storage/*.
- [ ] 6.5 Caches: src/main/java/org/traccar/database/DeviceLookupService.java; src/main/java/org/traccar/storage/MemoryStorage.java.
- [ ] 6.6 API/Web: src/main/java/org/traccar/api/*; src/main/java/org/traccar/web/*.
- [ ] 6.7 UI: traccar-web/*.

7. Criterios de aceptación
- [ ] 7.1 Aislamiento en endpoints/reportes/WebSocket.
- [ ] 7.2 Ingesta asociada al tenant correcto.
- [ ] 7.3 Permisos fuerzan tenantId y bloquean accesos indebidos.
- [ ] 7.4 Storage aplica tenant_id automaticamente.
- [ ] 7.5 Performance aceptable tras índices.

8. Riesgos y mitigaciones
- [ ] 8.1 Barrido de API por posibles fugas.
- [ ] 8.2 Evaluar performance por joins; considerar Fase 2.
- [ ] 8.3 Aserciones/logs para tenant_id en INSERT.
- [ ] 8.4 Pruebas de caché anti fugas.

9. Rollback
- [ ] 9.1 Preparar rollback de Liquibase para mt-*.
- [ ] 9.2 Flags para desactivar validaciones por tenant.
- [ ] 9.3 Ensayar restauración desde backups.

10. Anexos
- [ ] 10.1 Tablas a modificar: auditar y priorizar.
- [ ] 10.2 Adaptar esqueleto de changeSets a esquema real e idempotencia.

11. Despliegue
- [ ] 11.1 Preparar canary con flag OFF por defecto.
- [ ] 11.2 Ejecutar migraciones con monitoreo de locks.
- [ ] 11.3 Activar flag por porcentaje y observar métricas.
- [ ] 11.4 Procedimiento de rollback practicado.

12. Optimización Fase 2 (opcional)
- [ ] 12.1 Diseñar migración para tenant_id en positions/events.
- [ ] 12.2 Crear índices compuestos (tenant_id, deviceId, time) y probar impacto.
- [ ] 12.3 Backfill por lotes y validación.
- [ ] 12.4 Re-evaluar beneficio vs complejidad antes de prod.