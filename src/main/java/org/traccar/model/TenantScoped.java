package org.traccar.model;

/**
 * Interfaz marcador para entidades que pertenecen a un tenant específico.
 * <p>
 * Las entidades que implementan esta interfaz contienen un campo tenant_id
 * que determina a qué organización/empresa pertenecen en un entorno multi-tenant.
 * </p>
 * <p>
 * Esta interfaz permite aplicar políticas genéricas de:
 * - Filtrado automático en consultas (WHERE tenant_id = ?)
 * - Validación de permisos (user.tenantId == entity.tenantId)
 * - Auditoría y logging por tenant
 * </p>
 * 
 * @see org.traccar.storage.query.Request para filtros automáticos
 * @see org.traccar.api.security.PermissionsService para validaciones
 */
public interface TenantScoped {

    /**
     * Obtiene el ID del tenant al que pertenece esta entidad.
     * 
     * @return ID del tenant (clave foránea a tc_tenants.id)
     */
    long getTenantId();

    /**
     * Establece el ID del tenant al que pertenece esta entidad.
     * <p>
     * <strong>Nota:</strong> El tenant_id debe establecerse al crear la entidad
     * y no debe cambiarse después (inmutable en la práctica).
     * </p>
     * 
     * @param tenantId ID del tenant (debe ser válido y existir en tc_tenants)
     */
    void setTenantId(long tenantId);

}
