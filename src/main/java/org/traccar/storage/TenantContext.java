package org.traccar.storage;

/**
 * Contexto de tenant para la request actual.
 * <p>
 * Utiliza ThreadLocal para almacenar el tenant_id de la request HTTP actual,
 * permitiendo que las capas de Storage y QueryBuilder apliquen filtros automáticos
 * sin necesidad de pasar explícitamente el tenant_id en cada llamada.
 * </p>
 * <p>
 * <strong>Ciclo de vida:</strong>
 * 1. Un filtro web (TenantFilter) carga el tenantId al inicio de cada request
 * 2. Las capas de negocio usan getTenantId() para validar accesos
 * 3. QueryBuilder usa getTenantId() para filtrar consultas automáticamente
 * 4. El filtro web limpia el contexto al finalizar la request (finally)
 * </p>
 * <p>
 * <strong>Importante:</strong> Siempre debe limpiarse el contexto con clear() en un
 * bloque finally para evitar memory leaks en entornos con thread pools.
 * </p>
 * 
 * @see org.traccar.model.TenantScoped
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BYPASS_TENANT_FILTER = new ThreadLocal<>();

    private TenantContext() {
        // Clase de utilidad, no instanciable
    }

    /**
     * Establece el tenant_id para la request actual.
     * 
     * @param tenantId ID del tenant (debe ser > 0)
     * @throws IllegalArgumentException si tenantId <= 0
     */
    public static void setTenantId(long tenantId) {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId debe ser mayor que 0: " + tenantId);
        }
        TENANT_ID.set(tenantId);
    }

    /**
     * Obtiene el tenant_id de la request actual.
     * 
     * @return tenant_id, o null si no hay contexto establecido
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * Verifica si hay un tenant_id establecido en el contexto actual.
     * 
     * @return true si existe tenant_id, false en caso contrario
     */
    public static boolean hasTenantId() {
        return TENANT_ID.get() != null;
    }

    /**
     * Activa el bypass del filtro de tenant para la request actual.
     * <p>
     * Usado por SUPER_ADMIN para acceder a datos de cualquier tenant.
     * Debe usarse con extrema precaución y solo en operaciones administrativas.
     * </p>
     */
    public static void enableBypass() {
        BYPASS_TENANT_FILTER.set(true);
    }

    /**
     * Verifica si el bypass del filtro de tenant está activo.
     * 
     * @return true si el bypass está activo (SUPER_ADMIN), false en caso contrario
     */
    public static boolean isBypassEnabled() {
        return Boolean.TRUE.equals(BYPASS_TENANT_FILTER.get());
    }

    /**
     * Limpia completamente el contexto de tenant.
     * <p>
     * <strong>DEBE</strong> llamarse en un bloque finally al finalizar cada request
     * para evitar memory leaks en thread pools.
     * </p>
     * 
     * <pre>
     * try {
     *     TenantContext.setTenantId(tenantId);
     *     // ... procesar request ...
     * } finally {
     *     TenantContext.clear();
     * }
     * </pre>
     */
    public static void clear() {
        TENANT_ID.remove();
        BYPASS_TENANT_FILTER.remove();
    }

    /**
     * Obtiene información de debugging del contexto actual.
     * 
     * @return String con tenantId y estado de bypass
     */
    public static String getDebugInfo() {
        Long tenantId = getTenantId();
        boolean bypass = isBypassEnabled();
        return String.format("TenantContext[tenantId=%s, bypass=%s]", 
            tenantId != null ? tenantId : "null", bypass);
    }

}
