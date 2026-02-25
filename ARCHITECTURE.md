# Survey Response Sync Engine Architecture

## Overview

This document explains the architecture decisions behind the Survey Response Sync Engine. The system is designed for field agents working in rural areas with limited connectivity, low end devices, and battery constraints.

## Why This Architecture

The solution follows Clean Architecture principles with three layers: core, domain, and data. This separation was chosen for several practical reasons.

First, testability is critical for a sync engine. By separating concerns into distinct layers, we can test each component in isolation. The domain layer contains pure business logic with no Android dependencies. This means sync behavior can be verified with fast unit tests using fake implementations of repository and API interfaces.

Second, the architecture supports future changes without disrupting existing code. If we need to switch from Room to another database, only the data layer changes. If we need to add a new sync strategy, we modify the domain layer without touching storage code.

Third, keeping it simple was a priority. Unlike some Clean Architecture implementations that introduce many abstraction layers, this solution has just three layers with clear responsibilities. The core layer holds shared models like SyncError and SyncResult. The domain layer defines business rules and interfaces. The data layer implements storage and would implement real network calls.

The SyncEngine itself uses a sequential processing approach rather than parallel uploads. This decision protects battery life and reduces complexity. When network is unstable, parallel requests would drain battery faster and complicate error handling. Sequential processing also allows us to stop early when we detect network problems.

## Alternatives Considered

Several architectural alternatives were evaluated before settling on this approach.

**Outbox Pattern**: This pattern stores all pending operations in a dedicated outbox table and processes them through a separate worker. While robust for complex systems, it adds unnecessary complexity for our use case. The current approach of tracking status directly on survey responses achieves the same reliability with simpler code.

**Parallel Upload with Semaphore**: Uploading multiple responses simultaneously could improve throughput on stable connections. However, on low end devices with 1 to 2 GB RAM, parallel network operations increase memory pressure. The battery drain from maintaining multiple connections outweighs the speed benefit. For field agents working 8 to 12 hours without charging, sequential processing is the safer choice.

**Circuit Breaker Pattern**: This pattern would track failure rates and temporarily disable sync after repeated failures. While useful for high traffic systems, our consecutive failure detection achieves similar protection with simpler logic. The circuit breaker adds state management complexity that is not justified for a mobile client.

**Event Sourcing**: Storing all state changes as events would provide complete audit history. However, on devices with limited storage, the event log would grow quickly. The current approach stores only current state, which is sufficient for sync needs.

**SQLite without Room**: Direct SQLite access would reduce library overhead. However, Room provides compile time query verification, type converters, and coroutine integration that significantly reduce bugs. The small runtime overhead is acceptable for the development speed and reliability gains.

## Adding Media Compression Before Upload

To add media compression, I would introduce a MediaProcessor interface in the domain layer. This interface would define a method like compressImage that takes a file path and returns a compressed file path.

The implementation would live in the data layer, using Android's BitmapFactory to decode images and Bitmap.compress to reduce file size. The compression would happen before upload, not during survey collection. This approach keeps the original image available for retry if compression fails.

The SyncEngine would call the MediaProcessor before uploading each response. If compression fails, we would still attempt upload with the original file rather than failing the entire sync. The compressed files would be stored in a cache directory and deleted after successful upload along with the originals.

Configuration options would include target file size and image quality percentage. These would be adjustable based on network conditions or storage availability.

## When Network Detection Could Be Wrong

Network detection has several failure modes that developers must consider.

The most common issue is detecting network presence without actual connectivity. A device might show WiFi connected but the access point has no internet. Similarly, mobile data might show connected but the carrier network is congested or the data plan is exhausted.

Captive portals present another challenge. Hotel and airport WiFi often require authentication through a web page. The device reports network connectivity, but HTTP requests fail until the user completes authentication.

Network conditions can also change rapidly. A user might walk between areas with different coverage. The network might be available when we start sync but drop during upload. Our consecutive failure detection helps catch this scenario and stop early.

DNS issues can cause false negatives. The network might work fine, but DNS resolution fails. The device cannot determine if this is a network problem or a server problem.

The practical approach is to treat the actual upload result as the source of truth. We classify errors based on what happens during upload, not based on connectivity checks before upload. This avoids many edge cases where connectivity detection gives incorrect results.

## Supporting Remote Troubleshooting

To support remote troubleshooting when sync fails in the field, I would implement several features.

First, detailed sync logs should be stored locally. Each sync attempt would record timestamps, response IDs processed, errors encountered, and final status. These logs would have size limits to prevent storage exhaustion.

Second, a diagnostic endpoint could allow the user to share logs with support teams. This would create a compressed archive of recent sync logs and upload it when connectivity is available. The user would initiate this action and receive a reference code to share with support.

Third, error messages shown to the user should include actionable information. Instead of showing internal error codes, display messages like "Server is temporarily unavailable, your data is saved and will sync later" or "Photo upload failed, check storage space".

Fourth, a health check feature could verify server connectivity without uploading real data. This helps distinguish between network problems and data problems.

Finally, remote configuration could adjust sync behavior without app updates. Parameters like retry limits, backoff delays, and consecutive failure thresholds could be fetched from a server, allowing support teams to tune behavior based on field conditions.

## Technical Challenges with GPS Boundary Capture

Adding GPS boundary capture would introduce several technical challenges.

Battery consumption is the primary concern. GPS hardware consumes significant power, especially when tracking continuous movement around a field boundary. We would need to balance accuracy against battery life, possibly using lower accuracy location providers for initial positioning and high accuracy only during active boundary capture.

Location accuracy varies significantly. In rural areas, GPS signals may be weak due to terrain or vegetation. We might receive location updates with high uncertainty values. The system must decide whether to accept inaccurate points or wait for better readings.

Storage requirements increase substantially. A field boundary might contain hundreds of coordinate pairs. Storing these with timestamps, accuracy values, and associated survey data requires careful schema design.

Handling GPS failures gracefully is essential. The user might lose GPS signal during boundary capture. We need to decide whether to save partial boundaries, prompt the user to retry, or allow manual boundary drawing.

Background location tracking on modern Android requires special permissions and foreground service notifications. Users must understand why the app needs location access, and the system must comply with Android's strict background location policies.

Coordinate system transformations may be necessary. Survey data might need to integrate with GIS systems that use different projections. Converting between coordinate systems without losing precision requires careful implementation.

## One Improvement with More Time

If I had more time, I would implement a priority queue for sync ordering. Currently, responses sync in creation order. A smarter approach would prioritize responses based on several factors.

Responses with smaller payloads could sync first to maximize successful uploads when network is unstable. Older responses could receive higher priority to prevent data loss from storage cleanup. Responses without media attachments could sync before those with large images, providing faster initial sync completion.

The priority calculation would consider retry history. Responses that have consistently failed might be deprioritized to avoid blocking the queue with problematic data.

This improvement would make sync more efficient in challenging network conditions. Users would see more rapid progress on simpler surveys while complex media uploads happen when network is stable.

The implementation would require a priority scoring function and modifications to the getPendingResponses query to sort by calculated priority rather than creation time. Tests would verify that priority ordering produces expected behavior across different scenarios.
