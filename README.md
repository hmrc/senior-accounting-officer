# senior-accounting-officer

Backend service for the Senior Accounting Officer APIs.

## Local running

The app runs on port `10060`.

It currently proxies requests to `senior-accounting-officer-stubs`, which is expected on port `10061` via:

## Endpoint

Current route:

```text
PUT /senior-accounting-officer/subscriptions/:saoSubscriptionId
```

The endpoint accepts a raw JSON request body and forwards it to the downstream stubs service at:

```text
PUT /subscriptions/:saoSubscriptionId
```

The downstream response is passed through unchanged, including:
- `204` with no body on success
- `400` with the downstream JSON error payload on validation failures

Example request:

```bash
curl -i \
  -X PUT 'http://localhost:10060/senior-accounting-officer/subscriptions/123' \
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

## OpenApi Schema

This repository utilises [play-swagger](https://github.com/play-swagger/play-swagger) to generate an open api specification using a code-first approach.

The schema is generated upon running `sbt run`. It can be found in `/conf/openapi.json` from the root of the repository.


## Running tests with Bruno

Users can import the collection into Bruno and, once the required environment setup is complete, run the protected service requests directly.

### What is Bruno?

Bruno is an open-source API client used for manual exploratory testing and cross-team collaboration. It allows you to create, manage and run HTTP requests against a service directly from a collection stored in a repository.

### (HMRC) MDTP Recommendation

Bruno is the recommended API client tool for use on the MDTP platform, as detailed in the [MDTP Handbook](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/mdtp-test-approach/acceptance-testing/api-testing/index.html)

"MDTP recommends the Bruno API client tool for manual exploratory testing and cross-team collaboration. Only tools that have been reviewed and approved by Platform Security are permitted for use."

### Pre-requisites:
* Download and install Bruno.
* Start the needed services in a terminal session with the following command:

```bash
sm2 --start SAO_ALL
```

### Running Tests in the Local environment

* Open Bruno.
* From the Bruno menu select Open Collection.
* Navigate to the bruno folder in the repository.
* Click the Open button.
* In Bruno, select the local environment from the environment dropdown.
* Select a request from the collection in the left-hand panel.
* Click Send to execute the request.
* Verify the response status and body match the expected output.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

