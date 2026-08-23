[![CI/CD Pipeline](https://github.com/theandiman/recipe-management-service/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/theandiman/recipe-management-service/actions/workflows/ci-cd.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=theandiman_recipe-management-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=theandiman_recipe-management-service)
[![Sonar Tech Debt](https://img.shields.io/sonar/tech_debt/theandiman_recipe-management-service?server=https://sonarcloud.io)](https://sonarcloud.io/summary/new_code?id=theandiman_recipe-management-service)
[![Sonar Violations](https://img.shields.io/sonar/violations/theandiman_recipe-management-service?server=https://sonarcloud.io)](https://sonarcloud.io/summary/new_code?id=theandiman_recipe-management-service)
[![Known Vulnerabilities](https://snyk.io/test/github/theandiman/recipe-management-service/badge.svg)](https://snyk.io/test/github/theandiman/recipe-management-service)

# Recipe Management Service

A Spring Boot microservice for persisting and managing recipe data using Google Cloud Firestore.

## Overview

The Recipe Storage Service provides a REST API for storing, retrieving, and managing recipes. It integrates with Firebase Authentication for security and uses Firestore as the NoSQL database backend.

## Technology Stack

- **Java 21**
- **Spring Boot 3.4.0**
- **Firebase Admin SDK 9.3.0** - Authentication & Firestore
- **Google Cloud Firestore** - NoSQL database
- **Lombok** - Reduce boilerplate code
- **OpenAPI/Swagger** - API documentation
- **Maven** - Build & dependency management
- **Honeycomb** - Centralized observability (traces, metrics, logs)
- **OpenTelemetry** - Telemetry collection

## Observability

The service integrates with [Honeycomb](https://www.honeycomb.io/) for centralized observability, providing distributed tracing, metrics, and structured logging.

### Features

- ✅ **Distributed Tracing** - Track requests across service boundaries
- ✅ **Application Metrics** - JVM, HTTP, and custom business metrics
- ✅ **Structured Logging** - Consistent log format with trace correlation
- ✅ **Error Tracking** - Detailed error context and stack traces
- ✅ **Performance Monitoring** - Response times, throughput, and latency

### Local Development

1. **Set Honeycomb API Key**
   ```bash
   export HONEYCOMB_API_KEY=your_api_key_here
   ```

2. **Start with observability**
   ```bash
   ./start-with-observability.sh
   ```

   Or manually:
   ```bash
   # Download OpenTelemetry agent (latest version will be used automatically)
   curl -L -o opentelemetry-javaagent.jar \
     https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.21.0/opentelemetry-javaagent.jar

   # Start with agent
   java -javaagent:opentelemetry-javaagent.jar \
        -jar target/recipe-management-service-0.0.1-SNAPSHOT.jar
   ```

### Production Deployment

The OpenTelemetry Java agent is automatically included in the Docker image and configured via environment variables:

```yaml
# In your deployment configuration
environment:
  - HONEYCOMB_API_KEY=your_production_api_key
  - OTEL_SERVICE_NAME=recipe-management-service
  - OTEL_SERVICE_VERSION=0.0.1-SNAPSHOT
  - SERVICE_VERSION=0.0.1-SNAPSHOT  # Optional: override default version
```

### Viewing Traces & Metrics

- **Honeycomb UI**: https://ui.honeycomb.io/
- **Service Dashboard**: Filter by `service.name=recipe-management-service`
- **Trace Correlation**: All logs include `trace_id` and `span_id` for correlation

## Features

- ✅ Create and read operations for recipes
- ✅ Firebase Authentication integration
- ✅ Firestore database persistence
- ✅ OpenAPI/Swagger documentation
- ✅ CORS configuration for frontend integration
- ✅ Cloud Run deployment ready
- ✅ Comprehensive error handling

## Architecture

```
┌─────────────┐
│   Frontend  │
│  (React)    │
└──────┬──────┘
       │ HTTP + Firebase JWT
       ▼
┌─────────────────────────────┐
│  Recipe Storage Service     │
│  ┌───────────────────────┐  │
│  │ FirebaseAuthFilter    │  │ ← Validates JWT tokens
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ RecipeController      │  │ ← REST endpoints
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ RecipeService         │  │ ← Business logic
│  └───────────────────────┘  │
└──────────┬──────────────────┘
           │
           ▼
    ┌─────────────┐
    │  Firestore  │
    │  Database   │
    └─────────────┘
```

## API Endpoints

📘 **[API Documentation](https://theandiman.github.io/recipe-management-service/)** - View the interactive Swagger UI for full API details.

### User Profiles

Canonical profile documents are stored in `users/{uid}`. The document contains `displayName`,
`bio`, `avatarUrl`, `visibility`, `createdAt`, `updatedAt`, and service-derived
`followerCount`/`followingCount` fields. Clients can only manage their own profile:

```http
GET /api/users/me/profile
Authorization: Bearer <firebase-id-token>

PUT /api/users/me/profile
Authorization: Bearer <firebase-id-token>
Content-Type: application/json

{
  "displayName": "Chef Andy",
  "bio": "Home cook and pasta enthusiast",
  "avatarUrl": "https://example.com/avatar.png",
  "visibility": "PUBLIC"
}
```

`PUT` accepts only the editable fields above. Attempts to set UIDs, timestamps, or follow counts
return a documented `400` validation response. Public profile reads remain available at
`GET /api/users/{uid}/profile`; profiles marked `PRIVATE` omit personal fields and follow counts.

### Profile lifecycle and consistency

- `GET /api/users/me/profile` transactionally creates a missing canonical profile and repairs
  missing legacy metadata. It is safe to retry. A successfully authenticated caller receives a
  usable profile even if Firebase Auth's display-name/photo lookup is temporarily unavailable;
  that lookup supplies optional seed metadata only and never overwrites client-managed fields.
- Public reads are read-only. They use Firebase Auth only as a display-name/avatar fallback for
  public profiles with missing metadata. If an older recipe author has public recipes but no
  profile document or Firebase Auth record, the API returns a placeholder profile instead of
  failing the recipe-author view. Public reads do not create profile documents.
- `POST /api/users/me/profile/repair` is an authenticated, per-user repair operation. It safely
  backfills canonical metadata and rebuilds `followerCount` and `followingCount` from that
  user's two follow indexes. It is idempotent, runs in Firestore transactions, and never deletes
  profile, recipe, follow, avatar, or cache data. Follow and unfollow operations also clamp
  counters at zero, so persisted counters cannot become negative.
- The service has no account-delete, profile-delete, bulk cleanup, or anonymization endpoint.
  Deleting a Firebase Auth account does not automatically remove this service's recipes,
  profiles, follows, or avatar URL. Avatar objects and caches are owned by their respective
  services; this service stores only the avatar URL. Any future account-removal workflow must be
  explicitly authorized, scoped to one UID, previewed and test-covered before it removes or
  anonymizes recipes, follows, profile data, storage objects, or cached entries.

Lifecycle events are logged with the authenticated UID. Failed profile bootstrap or counter
reconciliation is logged as an error and returns `503`, allowing callers and operational
telemetry to distinguish an unavailable dependency from a missing legacy profile.

### Save Recipe
```http
POST /api/recipes
Authorization: Bearer <firebase-id-token>
Content-Type: application/json

{
  "title": "Spaghetti Carbonara",
  "description": "Classic Italian pasta",
  "ingredients": ["400g spaghetti", "200g pancetta", ...],
  "instructions": ["Boil pasta", "Fry pancetta", ...],
  "prepTime": 15,
  "cookTime": 20,
  "servings": 4,
  "nutrition": { "calories": 450, ... },
  "tips": {
    "substitutions": ["Use bacon instead of pancetta"],
    "storage": ["Store in airtight container for 3 days"]
  },
  "source": "ai-generated"
}
```

### Get Recipe by ID
```http
GET /api/recipes/{id}
Authorization: Bearer <firebase-id-token>
```

### Get User's Recipes
```http
GET /api/recipes
Authorization: Bearer <firebase-id-token>
```

### Update Recipe *(planned for future release)*
```http
PUT /api/recipes/{id}
Authorization: Bearer <firebase-id-token>
Content-Type: application/json

## Local Development

### Prerequisites

- Java 17+
- Maven 3.9+
- Firebase service account JSON file
- GCP project with Firestore enabled

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/theandiman/recipe-management-service.git
   cd recipe-management-service
   ```

2. **Set up Firebase credentials**
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/firebase-service-account.json
   ```

3. **Configure application properties**
   
   Create `src/main/resources/application-local.properties`:
   ```properties
   # Firebase
   firebase.project.id=your-firebase-project-id
   
   # Firestore
   firestore.collection.recipes=recipes
   
   # Authentication
   auth.enabled=true
   ```

4. **Build the project**
   ```bash
   mvn clean install
   ```

5. **Run the service**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

   The service will be available at `http://localhost:8081`

6. **Access API documentation**
   
   Open http://localhost:8081/swagger-ui.html

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `production` |
| `FIREBASE_PROJECT_ID` | Firebase project ID | - |
| `GOOGLE_APPLICATION_CREDENTIALS` | Path to Firebase service account JSON | `/secrets/firebase-sa.json` |
| `AUTH_ENABLED` | Enable/disable Firebase auth | `true` |
| `FIRESTORE_COLLECTION_RECIPES` | Firestore collection name | `recipes` |
| `FIRESTORE_COLLECTION_USERS` | Canonical profile collection name | `users` |
| `FIRESTORE_COLLECTION_FOLLOWS` | Follow-index collection name | `follows` |

## Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn verify

# Run specific test
mvn test -Dtest=RecipeServiceTest
```

## Deployment

### Cloud Run

The service is automatically deployed to Google Cloud Run via Cloud Build when changes are pushed to `main`.

**Deployment configuration:**
- Region: `europe-west2`
- Min instances: 0
- Max instances: 10
- CPU: 1
- Memory: 512Mi
- Port: 8081

**Cloud Build triggers on:**
- Push to `main` branch
- Pull request creation

See `cloudbuild.yaml` for full CI/CD configuration.

### Manual Deployment

```bash
# Build Docker image
docker build -t recipe-management-service .

# Tag for GCP Artifact Registry
docker tag recipe-management-service \
  europe-west2-docker.pkg.dev/PROJECT_ID/recipe-management/recipe-management-service:latest

# Push to registry
docker push europe-west2-docker.pkg.dev/PROJECT_ID/recipe-management/recipe-management-service:latest

# Deploy to Cloud Run
gcloud run deploy recipe-management-service \
  --image europe-west2-docker.pkg.dev/PROJECT_ID/recipe-management/recipe-management-service:latest \
  --region europe-west2 \
  --platform managed
```

## Authentication

The service uses Firebase Authentication with JWT tokens:

1. **Client obtains Firebase ID token** from Firebase Auth
2. **Client includes token** in `Authorization: Bearer <token>` header
3. **FirebaseAuthenticationFilter validates token** using Firebase Admin SDK
4. **User ID extracted** from token and added to request attributes
5. **Controller uses user ID** to scope recipe operations

**CORS Configuration:**
- Allowed origins: `http://localhost:5173`, Firebase Hosting URLs
- Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`
- Credentials: Enabled

## Project Structure

```
recipe-management-service/
├── src/main/java/com/recipe/storage/
│   ├── config/
│   │   ├── CorsConfig.java           # CORS configuration
│   │   ├── FirebaseConfig.java       # Firebase initialization
│   │   └── OpenApiConfig.java        # Swagger documentation
│   ├── controller/
│   │   └── RecipeController.java     # REST endpoints
│   ├── dto/
│   │   ├── CreateRecipeRequest.java  # Request DTO
│   │   └── RecipeResponse.java       # Response DTO
│   ├── filter/
│   │   └── FirebaseAuthenticationFilter.java  # JWT validation
│   ├── model/
│   │   └── Recipe.java               # Firestore entity
│   ├── service/
│   │   └── RecipeService.java        # Business logic
│   └── RecipeStorageApplication.java # Main application
├── src/main/resources/
│   ├── application.properties         # Default config
│   └── application-local.properties   # Local dev config
├── cloudbuild.yaml                    # CI/CD pipeline
├── Dockerfile                         # Container image
└── pom.xml                           # Maven dependencies
```

## Troubleshooting

### Service returns 403 Forbidden

**Issue:** Cloud Run IAM policy blocking requests

**Solution:** Ensure Cloud Run service allows unauthenticated access (Firebase Auth handles authentication at application level):
```bash
gcloud run services add-iam-policy-binding recipe-management-service \
  --region=europe-west2 \
  --member="allUsers" \
  --role="roles/run.invoker"
```

### Service returns 500 - Firestore database not found

**Issue:** Firestore database doesn't exist in GCP project

**Solution:** Create Firestore database:
```bash
gcloud firestore databases create \
  --location=europe-west2 \
  --project=YOUR_PROJECT_ID
```

### CORS errors in browser

**Issue:** OPTIONS preflight requests failing

**Solution:** Verify `CorsConfig` includes your frontend origin and `FirebaseAuthenticationFilter` allows OPTIONS requests without authentication.

### Local development - GOOGLE_APPLICATION_CREDENTIALS not set

**Issue:** Missing Firebase service account credentials

**Solution:** 
1. Download service account JSON from Firebase Console
2. Set environment variable:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
   ```

## Related Repositories

- [recipe-management-frontend](https://github.com/theandiman/recipe-management-frontend) - React TypeScript frontend
- [recipe-management-infrastructure](https://github.com/theandiman/recipe-management-infrastructure) - Terraform IaC
- [recipe-management-ai-service](https://github.com/theandiman/recipe-management-ai-service) - AI recipe generation

## Contributing

1. Create a feature branch
2. Make changes with tests
3. Ensure `mvn verify` passes
4. Create a pull request

## License

MIT
