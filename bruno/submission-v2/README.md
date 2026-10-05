# Submission v2 QA

Select your environment and run **auth → Auth enrolled**. The service needs MongoDB, downstream dependencies and `work-items.enabled=true`.

Run these checks for both **notification** and **certificate**. Request names show the expected status; check responses manually, as with the existing collection.

| Request | Check |
|---|---|
| post → (202) successful-request | Returns a key, saved automatically for subsequent requests. Each send creates a fresh submission. |
| get → (204) pending-request | Returns 204 while processing. Fast submissions may already return 200. |
| get → (200) successful-request | Repeat until 200 with `notificationRef` or `certificateRef`. Record the reference. |
| post → (202) duplicate-request | Returns the same key. Repeat the successful GET: reference stays unchanged. Check no duplicate DPS calls or recipient emails. |
| post → (409) changed-payload | Returns 409 with `IDEMPOTENCY_KEY_CONFLICT`. |
| get → (404) unknown-key | Returns 404 with `NOT_FOUND`. |
| post → (202) missing-idempotency-key | Returns a generated key, saved separately. |
| get → (200) generated-key | Repeat until 200 for the generated key. |

Run the successful POST before requests using its key. Run the missing-key POST before the generated-key GET. Keys are runtime variables; repeat the matching POST if Bruno clears them.

## Failure and recovery

Configure the downstream stub before a fresh successful POST. Restore it after observing the failure.

| Stub/action | Check |
|---|---|
| Subscription lookup returns 503 | GET stays pending; restore stub and poll until 200. First retry defaults to 60 seconds. |
| Initial PDF upload fails | GET reaches 200; restore stub and verify document delivery completes. |
| Email or SDES fails | GET remains 200; restore stub and verify failed work retries. |
| DPS POST times out | Wait for the operation timeout (default 60 seconds), then send **get → (502) dps-timeout**. Expect `DOWNSTREAM_SERVICE_UNAVAILABLE`; verify no second DPS POST, including after restart. |
| Restart with pending work | GET eventually reaches 200; completed operations are not repeated. |

Use downstream logs and MongoDB `submission-*` collections to check delivery, retries and duplicates. Run these requests individually; pending and failure cases need their stated setup.
