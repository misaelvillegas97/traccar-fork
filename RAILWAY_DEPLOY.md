# Deployment de Traccar en Railway

## Pasos para deployar:

### 1. Crear proyecto en Railway
```bash
railway login
railway init
```

### 2. Agregar base de datos PostgreSQL
En el dashboard de Railway:
- Click en "New" → "Database" → "PostgreSQL"
- Railway automáticamente creará estas variables de entorno:
  - `PGHOST`
  - `PGPORT`
  - `PGDATABASE`
  - `PGUSER`
  - `PGPASSWORD`

### 3. Configurar variables de entorno adicionales (opcional)
Si Railway no expone `PORT` automáticamente:
```bash
railway variables set PORT=8082
```

### 4. Deployar la aplicación
```bash
railway up
```

O hacer push a tu repositorio si conectaste Railway con GitHub.

### 5. Exponer puertos
En el dashboard de Railway:
- Ve a Settings → Networking
- Genera un dominio público o configura un puerto específico
- Asegúrate de exponer el puerto 8082 (o el que configuraste en PORT)

## Variables de entorno que usa Traccar

Railway provee automáticamente al agregar PostgreSQL:
- `PGHOST` - Host de la base de datos
- `PGPORT` - Puerto (usualmente 5432)
- `PGDATABASE` - Nombre de la base de datos
- `PGUSER` - Usuario de PostgreSQL
- `PGPASSWORD` - Contraseña de PostgreSQL
- `PORT` - Puerto de la aplicación (Railway lo asigna automáticamente)

## Verificar el deployment

Después del deployment:
1. Revisa los logs: `railway logs`
2. Verifica que la conexión a la BD sea exitosa
3. Accede a la URL generada por Railway

## Notas importantes

- El archivo `traccar-railway.xml` reemplaza las credenciales hardcodeadas con variables de entorno
- Railway automáticamente detecta el `Dockerfile.alpine` y lo usa para el build
- La base de datos debe estar lista antes de que Traccar inicie
