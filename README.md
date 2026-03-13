# senior-accounting-officer

Backend service for the Senior Accounting Officer APIs.

## Local running

The app runs on port `10060`.

It currently proxies requests to `senior-accounting-officer-stubs`, which is expected on port `10061` via:

## Endpoint

Current route:

```text
PUT /senior-accounting-officer/subscriptions
```

The endpoint accepts a raw JSON request body and forwards it to the downstream stubs service at:

```text
PUT /subscriptions
```

The downstream response is passed through unchanged, including:
- `204` with no body on success
- `400` with the downstream JSON error payload on validation failures

Example request:

```bash
curl -i \
  -X PUT 'http://localhost:10060/senior-accounting-officer/subscriptions' \
  -H 'Content-Type: application/json' \
  --data-raw '{
    "safeId": "XE000123456789",
    "company": {
      "companyName": "Acme Manufacturing Ltd",
      "uniqueTaxReference": "1234567890",
      "companyRegistrationNumber": "OC123456"
    },
    "contacts": [
      {
        "name": "Jane Doe",
        "email": "jane.doe@example.com"
      }
    ]
  }'
```

## Tests

Focused unit test:

```bash
sbt test
```

Focused integration test:

```bash
sbt it/test
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
