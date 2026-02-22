---
name: codebase-profiler
description: >
  Automated codebase profiling skill. Scans existing systems to generate
  module dependencies, domain models, API inventory, DB relationships,
  and business flow catalogs.
trigger: when running /forge-profile or analyzing an existing codebase
tags: [profiling, analysis, knowledge-mining, architecture]
supported-languages: [java, kotlin, csharp, python, go, typescript]
version: "2.0"
scope: platform
category: foundation
---

# Codebase Profiler

## Purpose
Automatically scan and profile existing codebases to generate structured knowledge for the knowledge-base.

## Profiling Steps

### 1. Module Dependency Analysis
- Scan `settings.gradle.kts` / `build.gradle.kts` for module structure
- Extract inter-module dependencies from `project(":module")` declarations
- Generate Mermaid dependency graph

### 2. Domain Model Catalog
- Find all `@Entity` classes and `data class` models
- Map relationships (`@OneToMany`, `@ManyToOne`, `@ManyToMany`)
- Generate Mermaid class diagram
- Document field types, constraints, validations

### 3. API Inventory
- Scan all `@Controller` / `@RestController` classes
- Extract endpoints: method, path, request/response types
- Map to OpenAPI spec if available
- Document authentication requirements per endpoint

### 4. Database Relationship Map
- Scan Flyway migrations for schema structure
- Extract foreign key relationships
- Generate ER diagram (Mermaid)
- Document indexes and constraints

### 5. Business Flow Catalog
- Identify service method call chains
- Map event-driven flows (listeners, publishers)
- Document async vs sync communication patterns
- Generate sequence diagrams for key flows

## Output Format
Save to `knowledge-base/profiles/{system-name}/`:
- `overview.md` — System summary
- `modules.md` — Module dependency graph
- `domain-model.md` — Entity/model catalog
- `api-inventory.md` — API endpoint catalog
- `database-schema.md` — ER diagram + schema docs
- `business-flows.md` — Key flow sequence diagrams

---

## .NET Project Parsing

### Solution & Project Structure
- **`.sln` files** → Parse solution structure to identify all projects and their organization
- **`.csproj` files** → Extract:
  - Target framework (e.g., `net8.0`, `net6.0`)
  - NuGet package references (`<PackageReference>`)
  - Project-to-project references (`<ProjectReference>`)
  - Build properties and conditional compilation

### ASP.NET MVC / WebAPI
- **Controllers**: Scan classes inheriting `ControllerBase` / `Controller` / decorated with `[ApiController]`
  - Extract `[Route]`, `[HttpGet]`, `[HttpPost]`, `[Authorize]` attributes
  - Map action methods to HTTP endpoints
- **Minimal APIs**: Scan `app.MapGet()`, `app.MapPost()` patterns in `Program.cs`

### Services & Repositories
- Scan for DI registrations in `Program.cs` / `Startup.cs`:
  - `builder.Services.AddScoped<IService, ServiceImpl>()`
  - `builder.Services.AddTransient<>()`, `AddSingleton<>()`
- Map interface-to-implementation bindings

### Entity Framework & Data Access
- **DbContext classes**: Scan for `DbSet<T>` properties → entity catalog
- **EF Migrations**: Parse `Migrations/` folder for schema evolution history
- **Fluent API**: Scan `OnModelCreating()` for relationship configuration
- **Data Annotations**: `[Key]`, `[ForeignKey]`, `[Required]`, `[MaxLength]`, `[Table]`

### Domain Models
- Scan for record types, POCO classes in `Models/`, `Domain/`, `Entities/` directories
- Map inheritance hierarchies and value objects
- Document validation attributes (`[Required]`, `[Range]`, `[RegularExpression]`)

### .NET Output Format
Same as Java/Kotlin output: `overview.md`, `modules.md`, `domain-model.md`, `api-inventory.md`, `database-schema.md`, `business-flows.md`

---

## Python Project Parsing

### Project Structure
- **`pyproject.toml`** / **`setup.py`** / **`setup.cfg`** → Package metadata, dependencies
- **`requirements.txt`** / **`Pipfile`** / **`poetry.lock`** → Dependency pinning
- **`__init__.py`** files → Package/module structure

### Django
- `settings.py` → Installed apps, middleware, database config
- `models.py` → ORM models (Field types, ForeignKey, ManyToMany)
- `views.py` / `viewsets.py` → API endpoints
- `urls.py` → URL routing patterns
- `serializers.py` → DRF serializers
- `admin.py` → Admin registrations
- `migrations/` → Schema evolution

### FastAPI
- `@app.get()`, `@app.post()` → Endpoint definitions
- Pydantic models → Request/response schemas
- `Depends()` → Dependency injection patterns
- SQLAlchemy models → Database entities

---

## Go Project Parsing

### Project Structure
- **`go.mod`** → Module path, Go version, dependencies
- **`go.sum`** → Dependency checksums
- Package layout: `cmd/`, `internal/`, `pkg/`, `api/`

### Key Patterns
- `http.HandleFunc()` / `mux.HandleFunc()` → HTTP routes
- `gin.Engine` / `echo.Echo` / `chi.Router` → Framework-specific routing
- Struct definitions with `json:` tags → API models
- Interface definitions → Service contracts
- `*sql.DB` / GORM / sqlx → Database access patterns
