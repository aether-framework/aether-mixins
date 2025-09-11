![License](https://img.shields.io/badge/license-MIT-red)
![Maven Central](https://img.shields.io/maven-central/v/de.splatgames.aether/aether-mixins)
![Version](https://img.shields.io/badge/version-0.1.0-orange)

# Aether Mixins ⚙️

**Aether Mixins** is a lightweight and safe **bytecode weaving framework** for the JVM.
It enables **deterministic load-time (agent)** and **runtime (in-app)** class modifications through a clean,
annotation-based API — inspired by SpongePowered Mixins, with a strong focus on **developer experience**, **verification
**, and **safety**.

---

## ✨ Features (v0.1.0 MVP)

- ✅ **Agent & In-App Weaving** — Use `-javaagent` for early weaving or attach a transformer at runtime
- ✅ **Annotation API** — `@Mixin`, `@Inject(HEAD|TAIL)`, `@Redirect(...)`
- ✅ **Refmap model** — YAML config + JSON refmaps to map symbolic specs to concrete `(owner, name, desc)`
- ✅ **ASM backend** — Precise, minimal ASM visitors for the MVP join points
- ✅ **Safety-first** — Optional safe-mode, frame recomputation policy, structured diagnostics
- ✅ **JDK 17+** — Built and tested on modern LTS JVMs

> The MVP focuses on a small, robust core you can build on. Additional join points and helpers are on the roadmap.

---

## 📦 Modules

- **aether-mixins-core** — Annotations (`@Mixin`, `@Inject`, `@Redirect`), refmap model & loaders, planning interfaces
- **aether-mixins-bytecode** — ASM-based weaving adapters (HEAD/TAIL injects, invoke redirects)
- **aether-mixins-runtime** — High-level runtime API (config discovery, planning, weaving)
- **aether-mixins-processor** — Annotation processor that generates refmaps + a minimal `mixins.yml`
- **aether-mixins-agent** — Java agent (premain/agentmain) with per-class planning & retransformation

---

## 🚀 Quickstart

### 1) Run with the agent

```text
-javaagent:/path/to/aether-mixins-agent.jar -Daether.mixins.config=/path/to/mixins.yml -jar yourapp.jar
```

### 2) Minimal configuration

```yml
version: 1
mixins:
  - name: core
    files:
      - mixins/app.refmap.json
runtime:
  safe_mode: true
  verify_frames: strict
```

### 3) Build-time refmap generation (optional)

Use the **annotation processor** to generate `mixins.yml` and a refmap from your `@Mixin` classes automatically.

---

## 📚 Installation

**Maven**

```xml
<dependency>
    <groupId>de.splatgames.aether</groupId>
    <artifactId>aether-mixins-runtime</artifactId>
    <version>0.1.0</version>
</dependency>
```

**Gradle (Groovy)**

```groovy
dependencies {
    implementation 'de.splatgames.aether:aether-mixins-runtime:0.1.0'
}
```

**Gradle (Kotlin)**

```kotlin
dependencies {
    implementation("de.splatgames.aether:aether-mixins-runtime:0.1.0")
}
```

> Depend on **runtime** to run weaving; add **processor** in your annotation processing path to auto-generate refmaps.

---

## 🔒 Verification & Safety

- **Frame policy** — Recompute frames/maxs under configurable verification levels
- **Safe-mode** — Continue startup and record errors instead of failing hard
- **Deterministic planning** — Stable ordering for specs within a method
- **Diagnostics** — Structured `ConfigProblems`, per-class paths, optional dumps

---

## 🧪 Testing

- In-JVM weaving for deterministic assertions
- Golden-file style comparison of input/output classes
- CI matrix targeting JDK 17/21

---

## 🗺️ Roadmap

- **v0.1.0** (current)
    - Agent & in-app weaving
    - ASM backend with `@Inject(HEAD|TAIL)` and `@Redirect`
    - Refmap model + YAML config
    - Basic verification & diagnostics
    - Annotation processor (refmap + minimal config generation)

- **v0.2.0**
    - Replace MVP shortcuts with **full logic** for additional join points (`@At(INVOKE/RETURN/LINE)`)
    - Improved conflict resolution & ordering
    - Richer diagnostics, better error surfaces, configuration validation
    - Remapping hooks support and descriptor bridging improvements

- **v0.3.0**
    - `@ModifyArg` / `@ModifyVar`
    - Dev hot-reload quality-of-life features
    - Build tooling integration (Gradle/Maven tasks) and mapping adapters

- **v1.0.0**
    - Stable annotation surface & full docs with production examples

---

## 🤝 Contributing

Contributions welcome! Please open issues/PRs with clear repros or targeted patches.

---

## 📄 License

MIT © Splatgames.de Software and Contributors
