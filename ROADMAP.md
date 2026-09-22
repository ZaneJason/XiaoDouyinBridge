# XiaoDouyinBridge Roadmap

XiaoDouyinBridge is under active development. The roadmap below reflects the current technical direction and is intentionally conservative: items are listed only when they are planned or already partially implemented.

## Near term

- [ ] Validate the launcher against the current official Douyin Live Companion PipeSDK package
- [ ] Exercise `OPEN_LIVE_DATA` end-to-end with real comment and fans-club events
- [ ] Verify clean launcher shutdown for `OPEN_WIN_CLOSE`, `EVENT_DISCONNECTED`, and reset/broken pipe conditions
- [ ] Add repeatable integration tests for Bridge event parsing and binding flows
- [ ] Document a minimal production deployment for Bridge Server + MariaDB + Minecraft

## Reliability and security

- [ ] Expand replay/idempotency tests around `msg_id` handling
- [ ] Add configuration validation for missing or unsafe secrets
- [ ] Improve operational diagnostics without logging secret values
- [ ] Add schema migration/versioning guidance for long-running deployments

## Developer experience

- [ ] Add automated checks for documentation and configuration examples
- [ ] Add a reproducible local integration-test setup
- [ ] Publish versioned release notes once the first stable release is ready

## Non-goals

- Redistributing proprietary Douyin SDK binaries
- Guessing or reimplementing undocumented vendor ABI behavior
- Committing real production credentials or platform secrets

Roadmap items may change as Douyin platform requirements and SDK behavior evolve.
