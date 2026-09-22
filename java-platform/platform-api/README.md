# platform-api

Framework-neutral API boundary for REST and SSE.

## Current scope

- `PlatformApiFacade` maps REST-style request DTOs to application use cases.
- Task creation returns the accepted task shape for `POST /api/tasks`.
- Task event listing returns SSE-ready event DTOs for `/api/tasks/{taskId}/events`.
- Approval listing and decision endpoints use `ApprovalUseCase`.

Spring MVC/WebFlux controllers should stay thin and delegate to this facade.
