# GC G1 Viewer

Standalone desktop tool for analyzing **JDK unified G1 GC logs** from JBoss EAP / WildFly and **Red Hat Single Sign-On** (RH-SSO). Runs entirely on your machine — no log upload to third-party services.

## Features

- **JVM Memory Size** — summary table and allocated vs peak chart (Young, Old, Humongous, Metaspace)
- **Heap after GC / Heap before GC** — heap usage over time
- **Pause GC Duration** — stop-the-world pause times per GC event
- **Reclaimed Bytes** — memory reclaimed per GC
- **Young Gen** — allocated, before GC, and after GC (from G1 Eden/Survivor regions)
- **Meta Space** — capacity vs used over time
- **Performance metrics** — throughput %, total/average/longest/median pause, event counts
- **GC event table** — detailed list with phases, regions, metaspace, CPU
- **Real-time watch** — tail a live GC log file while RH-SSO / JBoss is running

## Requirements

- Java 17 or newer
- Maven 3.8+ (to build)

## Build

```bash
mvn clean package
```

Produces: `target/gc-g1-viewer.jar`

## Run

```bash
java -jar target/gc-g1-viewer.jar
java -jar target/gc-g1-viewer.jar /path/to/gc.log
```

Or use the script:

```bash
./run.sh
./run.sh gc-2026-05-27_12-20-09.log
```

## Enable GC logging on JBoss / RH-SSO

Add JVM options (e.g. in `standalone.conf` or `JAVA_OPTS`):

```text
-Xlog:gc*,safepoint:file=/var/log/sso/gc-%t.log:time,uptime,level,tags
```

For rotation:

```text
-Xlog:gc*:file=/var/log/sso/gc-%t.log:time,uptime,level,tags:filecount=5,filesize=20M
```

Then open or **Watch** the log file in this viewer.

## Watch live over SSH (RH-SSO remote server)

`scp` only copies a snapshot at one moment; it does not stream new GC lines. For live analysis:

**Option A — script (recommended)**

```bash
chmod +x scripts/watch-remote-gc.sh
./scripts/watch-remote-gc.sh admin@rhsso-lab-1 /opt/rhsso/standalone/log/gc.log
```

This runs `ssh … tail -F` into `~/gc-live-rhsso.log` and opens the viewer with `--watch`.

**Option B — manual (two terminals)**

Terminal 1 — stream remote log to your machine:

```bash
ssh admin@rhsso-lab-1 "tail -n +1 -F /opt/rhsso/standalone/log/gc.log" >> ~/gc-live.log
```

Terminal 2 — watch the local copy:

```bash
java -jar target/gc-g1-viewer.jar --watch ~/gc-live.log
```

**Option C — SSHFS** — mount `/opt/rhsso/standalone/log` locally, then use **Watch Live** on the mounted file path.

Use a stable path if JBoss rotates logs (e.g. `gc.log` symlink) or pass the current `gc-YYYY-MM-DD_*.log` file name.

## Sample logs

This repository includes sample GC logs from a JBoss standalone RH-SSO instance:

- `gc-2026-05-27_12-14-33.log`
- `gc-2026-05-27_12-20-09.log`
- `gc-2026-05-27_12-20-26.log`

The app auto-loads the first `gc-*.log` in the working directory on startup.

## Interpreting metrics

| Metric | Meaning |
|--------|---------|
| **Throughput** | Approx. % of time spent in application code vs GC pauses (higher is better) |
| **Total GC Pause** | Sum of all stop-the-world GC pause durations in the analyzed window |
| **Longest Pause** | Worst single pause — key for SLA / latency tuning |
| **Pause by Reason** | Where time is spent (Young vs Remark vs metadata triggers) |

Use these to tune `-Xms`, `-Xmx`, `-XX:MaxGCPauseMillis`, G1 region size, and metaspace settings.

## License

MIT
