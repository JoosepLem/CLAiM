```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'lineColor': '#999999'}}}%%
C4Component
    title Component diagram for Web Application — Auth & Tenant Resolution

    Person(employee, "Medical Centre Employee", "Logs in with username. Views overview and invoice details after authentication.")

    Container_Boundary(spring_boot, "Web Application") {
        Component(login, "Login Controller", "Thymeleaf + Spring MVC", "Renders login page (`/login`) with single username input field. On success redirects to dashboard.")

        Component(auth, "Auth Controller", "Spring Security + JWT", "Username-based login. Validates user against public.users table, issues tenant-scoped JWT in secure HttpOnly SameSite=Strict cookie.")

        Component(tenant_int, "Tenant Interceptor", "Spring HandlerInterceptor", "Extracts tenant ID from JWT claim on every request, sets TenantContext. Enables schema-scoped queries by setting PostgreSQL search_path.")

        Component(dashboard, "Dashboard Controller", "Thymeleaf + Spring MVC", "Protected overview page (`/dashboard`). Landing page after login. All subsequent pages are tenant-scoped via the interceptor.")
    }

    ContainerDb(postgres, "Database", "PostgreSQL on AWS RDS", "public.users table maps username to tenant_id (schema name). Read-only for app_user.")

    Rel(employee, login, "Enters username", "HTTPS")
    Rel(login, auth, "Delegates authentication")
    Rel(auth, tenant_int, "Stores tenant in security context")
    Rel_Back(tenant_int, dashboard, "Provides tenant-scoped context")
    Rel(employee, dashboard, "Views overview after login", "HTTPS")
    Rel(auth, postgres, "SELECT username -> tenant_id", "SQL/TCP (SSL)")
```
