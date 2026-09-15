# GitHub About (owner token)

The GitHub About box is **not** set from this repository. `PATCH /repos/{owner}/{repo}` and topics return **403** for GitHub App / cloud-agent integration tokens. A logged-out browser cannot do it either.

Run this on a machine where `gh` is authenticated as **Misyk8925**:

```bash
gh auth login   # if needed
./scripts/set-github-about.sh
```

That is the same copy as the first lines of the root README:

| Field | Value |
|---|---|
| Description | Matching platform: CQRS deck reads, transactional outbox, mTLS service boundaries, role-aware gateway limits. |
| Website | https://lunari.misyk.tech |
| Topics | `java` `spring-boot` `quarkus` `apache-kafka` `cqrs` `keycloak` `matching` `microservices` `redis` `postgresql` |

Then pin issues [#35](https://github.com/Misyk8925/tinder-clone/issues/35), [#36](https://github.com/Misyk8925/tinder-clone/issues/36), [#37](https://github.com/Misyk8925/tinder-clone/issues/37) on the Issues page (also 403 for the integration).

Until that runs, GitHub.com still shows an empty About on `main`. Recruiters who only open the repo home see whatever is on **default branch**, not this PR.
