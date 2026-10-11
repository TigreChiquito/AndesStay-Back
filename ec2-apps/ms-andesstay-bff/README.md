# ms-andesstay-bff

*Backend for Frontend* de AndesStay. Es la **única puerta pública** del backend: el AWS API Gateway le reenvía todo el tráfico del front, y el BFF valida el token de Azure AD, aplica la autorización por rol y reenvía la petición al microservicio que corresponda.

No tiene base de datos ni mensajería.

## Responsabilidades

- **Resource server OAuth2:** valida firma, emisor, audiencia y expiración del JWT emitido por Azure AD (Entra ID).
- **Autorización por rol:** traduce los App Roles del claim `roles` a authorities `ROLE_*` y protege cada ruta.
- **Gateway:** reenvía `/api/<segmento>/**` al micro configurado para ese segmento.
- **CORS** para el front, que corre en otro origen.

## Endpoints

El BFF no tiene lógica de negocio: sus endpoints son la salud del servicio y el proxy hacia los micros.

| Método | Endpoint | Para qué sirve | Acceso | Respuestas |
|---|---|---|---|---|
| `GET` | `/actuator/health` | Indica si el BFF está arriba. Se puede consultar desde fuera: `<api-gateway>/actuator/health` | Público | 200 `{"status":"UP"}` |
| `GET` | `/actuator/info` | Información básica del servicio | Público | 200 |
| todos | `/api/**` | Reenvía la petición al micro que corresponde según la tabla de abajo, después de validar el token y el rol | Según la [tabla de roles](#autorización-por-rol) | La del micro; 401/403 si falla la seguridad; 404 si no hay ruta; 502 si el micro no responde |

Los endpoints de negocio están documentados en el README de cada micro: [reservations](../ms-andesstay-reservations/README.md#api-rest), [catalog](../ms-andesstay-catalog/README.md#api-rest), [report](../ms-andesstay-report/README.md#api-rest), [audit](../ms-andesstay-audit/README.md#api-rest) y [notify](../ms-andesstay-notify/README.md#api-rest) (avisos y vouchers).

## Enrutado

[`GatewayController`](src/main/java/cl/tigrechiquito/ms_andesstay_bff/controller/GatewayController.java) toma el primer segmento después de `/api/` y busca su URL base en `gateway.routes`:

| Ruta entrante | Clave | Destino (en ec2-apps) |
|---|---|---|
| `/api/reservations/**` | `reservations` | `http://reservations:8081` |
| `/api/units/**` | `units` | `http://catalog:8082` |
| `/api/reports/**` | `reports` | `http://report:8084` |
| `/api/audit/**` | `audit` | `http://audit:8085` |
| `/api/notifications/**` | `notifications` | `http://notify:8083` |
| `/api/vouchers/**` | `vouchers` | `http://notify:8083` |

Reenvía el método, el path completo, el query string, el body y el `Content-Type`. De la respuesta conserva el `Content-Type` y el `Content-Disposition`; este último sirve para que el PDF del voucher se descargue con su nombre. Si el micro responde con error 4xx o 5xx, el BFF lo devuelve tal cual. Además:

- **404** si el segmento no tiene ruta configurada.
- **502** (sin cuerpo) si el micro de destino no responde: está caído, reiniciándose o no resuelve por nombre.

### Propagación de identidad

El BFF no reenvía el header `Authorization`. En su lugar, agrega dos headers a partir del token ya validado:

| Header | Contenido |
|---|---|
| `X-User-Id` | Claim `oid` del token: el id del usuario en Azure AD, igual al `localAccountId` de MSAL |
| `X-User-Roles` | App Roles separados por coma, por ejemplo `Cliente` o `Recepcionista,Admin` |

Los micros los usan para reglas de pertenencia: reservations solo deja que un Cliente cancele sus propias reservas, y notify devuelve a cada usuario solo sus avisos. Los headers que mande el cliente **nunca** se reenvían, así que no se pueden falsificar desde fuera. Eso sí, los micros confían en la red interna de Docker: cualquiera con acceso directo a ella podría mandarlos.

## Autorización por rol

Definida en [`SecurityConfig`](src/main/java/cl/tigrechiquito/ms_andesstay_bff/config/SecurityConfig.java):

| Ruta | Método | Acceso |
|---|---|---|
| `OPTIONS /**` | OPTIONS | Público (preflight CORS) |
| `/actuator/health`, `/actuator/info` | GET | Público |
| `/api/units/**` | GET | Cualquier usuario autenticado |
| `/api/units/**` | POST / PUT / DELETE | `Admin` |
| `/api/reservations` | POST | `Cliente`, `Recepcionista`, `Admin` |
| `/api/reservations/*/status` | PUT | `Recepcionista`, `Admin` |
| `/api/reservations/*/cancel` | POST | `Cliente`, `Recepcionista`, `Admin`. El Cliente solo puede cancelar sus propias reservas (lo valida reservations) |
| `/api/reservations/**` | GET | Cualquier usuario autenticado |
| `/api/reports/**` | todos | `Admin` |
| `/api/audit/**` | todos | `Auditor`, `Admin` |
| `/api/notifications/**` | todos | Autenticado (notify filtra por usuario y rol) |
| `/api/vouchers/**` | GET | Autenticado (notify valida que el voucher sea del usuario, salvo para el personal) |
| cualquier otra | todos | Autenticado |

Sin token, o con un token inválido, la respuesta es **401**; el motivo exacto viene en el header `WWW-Authenticate`. Con un token válido pero sin el rol necesario, la respuesta es **403**.

> `POST /api/units/{id}/reserve` y `/release` también caen en la regla de escritura de `/api/units/**` (Admin). En el flujo normal no pasan por el BFF: reservations llama a catalog directamente por la red interna.

## Requisitos del token (Azure AD)

```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: https://login.microsoftonline.com/${AZURE_TENANT_ID}/v2.0
  audiences: [ api://${AZURE_API_CLIENT_ID}, ${AZURE_API_CLIENT_ID} ]
```

- El front debe pedir el scope `api://<client-id>/access_as_user`. El token de Microsoft Graph (`User.Read`) no sirve.
- El token debe ser **v2**: en el manifest del App Registration, `requestedAccessTokenVersion: 2`. Si no, el emisor sale como `https://sts.windows.net/...` y la respuesta es 401.
- Los roles deben estar asignados al usuario en *Enterprise applications → Users and groups* para que aparezcan en el claim `roles`.

## CORS

Orígenes permitidos en `cors.allowed-origins`, separados por coma, desde la variable `CORS_ALLOWED_ORIGINS` (en el compose: `${FRONT_ORIGIN}`). Si la variable no existe, el valor por defecto es `http://localhost:4200`.

Métodos: `GET, POST, PUT, DELETE, PATCH, OPTIONS`. Headers: `Authorization, Content-Type, Accept, Origin`. Sin credenciales (el token viaja en `Authorization`, no en cookies).

> En el despliegue actual, el AWS API Gateway también tiene CORS configurado y responde los preflight él mismo.

## Configuración

| Propiedad | Variable de entorno | Default (`application.yml`) |
|---|---|---|
| `server.port` | — | `8080` |
| `gateway.routes.reservations` | `GATEWAY_ROUTES_RESERVATIONS` | `http://localhost:8081` |
| `gateway.routes.units` | `GATEWAY_ROUTES_UNITS` | `http://localhost:8082` |
| `gateway.routes.reports` | `GATEWAY_ROUTES_REPORTS` | `http://localhost:8084` |
| `gateway.routes.audit` | `GATEWAY_ROUTES_AUDIT` | `http://localhost:8085` |
| `gateway.routes.notifications` | `GATEWAY_ROUTES_NOTIFICATIONS` | `http://localhost:8083` |
| `gateway.routes.vouchers` | `GATEWAY_ROUTES_VOUCHERS` | `http://localhost:8083` |
| issuer / audiencia | `AZURE_TENANT_ID`, `AZURE_API_CLIENT_ID` | — (obligatorias) |
| `cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |

## Estructura

```
ms_andesstay_bff/
├── config/
│   ├── GatewayProperties    mapa segmento → URL base (prefijo gateway.routes)
│   └── SecurityConfig       resource server JWT, reglas por rol, CORS
└── controller/
    └── GatewayController    proxy /api/** hacia los micros
```

## Ejecutar localmente

Requiere los micros de destino corriendo en `localhost` y las variables de Azure AD:

```bash
AZURE_TENANT_ID=... AZURE_API_CLIENT_ID=... CORS_ALLOWED_ORIGINS=http://localhost:5173 ./mvnw spring-boot:run
```

Health check: `GET http://localhost:8080/actuator/health`.
