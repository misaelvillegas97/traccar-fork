package org.traccar.model;

import org.traccar.storage.StorageName;

import java.util.Date;

/**
 * Entidad que representa un tenant (organización/empresa) en el sistema multi-tenant.
 * <p>
 * Cada tenant representa una organización independiente con sus propios usuarios,
 * dispositivos, grupos y configuraciones. El aislamiento de datos se logra mediante
 * la columna tenant_id en las tablas principales.
 * </p>
 * <p>
 * Estados posibles:
 * - 1: ACTIVE (activo, operando normalmente)
 * - 2: SUSPENDED (suspendido, acceso bloqueado)
 * - 3: INACTIVE (inactivo, datos preservados pero sin uso)
 * </p>
 */
@StorageName("tc_tenants")
public class Tenant extends ExtendedModel {

    /**
     * Nombre de la organización/empresa.
     * Ejemplo: "ACME Corporation", "Transport Solutions Ltd"
     */
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Slug único para identificar el tenant en URLs/subdominios.
     * Debe ser alfanumérico en minúsculas, sin espacios.
     * Ejemplo: "acme", "transport-sol", "empresa123"
     */
    private String slug;

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    /**
     * Estado del tenant.
     * 1 = ACTIVE, 2 = SUSPENDED, 3 = INACTIVE
     */
    private short status = 1;

    public short getStatus() {
        return status;
    }

    public void setStatus(short status) {
        this.status = status;
    }

    /**
     * Fecha y hora de creación del tenant.
     */
    private Date createdAt;

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Verifica si el tenant está activo.
     * 
     * @return true si status == 1 (ACTIVE)
     */
    public boolean isActive() {
        return status == 1;
    }

    /**
     * Verifica si el tenant está suspendido.
     * 
     * @return true si status == 2 (SUSPENDED)
     */
    public boolean isSuspended() {
        return status == 2;
    }

}
