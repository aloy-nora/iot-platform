# 08 · CI/CD（GitHub Actions + GitOps 闭环）

CI 在 `.github/workflows/ci.yml`；CD 由 ArgoCD 拉起（见 `09`）。

## CI 流程（push → 镜像进 GHCR）

- 触发：`on: push: branches:[master]`。**单位是一次 push（非每个 commit）**：一次推多个 commit 只跑一次。
- 步骤：checkout → setup-java 21(+gradle 缓存) → `./gradlew bootJar` → 登录 GHCR → build-push 镜像。
- **镜像仓库 GHCR**：`ghcr.io/<owner>/iot-backend`，tag 打 `latest` + `<sha>`。
  - 登录用**自动注入的 `GITHUB_TOKEN`**，无需配任何密钥；推包要 `permissions: packages: write`。
  - GHCR 包默认 **private**，节点匿名拉需**设为 public**（仓库 Packages → 包 → settings → Change visibility）。
- 暂不跑 test（CI 无 DB，集成测试会挂）；后续接测试基建再开。

## CD 闭环：让"改代码 → 自动上线"成立

- **坑：不能用 `:latest`**。ArgoCD 认的是"渲染结果的 diff"，values 永远写 `:latest` → 无 diff → 不重部署（即使 GHCR 上 latest 变了）。
- **正解：CI 打不可变的 `:<sha>` tag，并把新 sha 写回 `values.yaml` 再推回** → `values.yaml` 的变化才是触发 ArgoCD 的信号。
- CI 写回步骤要点：
  - `permissions: contents: write`（CI 要推 commit）；
  - **detached HEAD**：CI 的 checkout 停在触发的那个 SHA 上（为可复现，不是"master 现在指哪"），没有"当前分支" → 推用 **`git push origin HEAD:master`**（refspec `源:目标`，把当前提交明确推到远程 master，不管 detached）；
  - **防递归**：用默认 `GITHUB_TOKEN` 自推的 commit **不会再触发 workflow**（GitHub 内置防递归）+ commit 带 **`[skip ci]`** 双保险；
  - 用 `env:` 承接 `github.*` 上下文再进 `run:`，避免命令注入。
- **本地要 `git pull`**：CI 机器人往 master 推了 commit，本地会落后，改动前先 pull，否则 push 冲突。

## 架构权衡：单仓 write-back vs 双仓（面试加分点）

- 我们是**单仓**（源码 + 清单同仓）+ write-back：简单，但 **bot commit 夹进 app 历史**。
- **主流/成熟做法：拆两个仓**（ArgoCD 官方推荐）——
  - **app 仓**：源码 + CI（只构建推镜像），历史**只有人的 commit，干净**；
  - **config 仓（GitOps 仓）**：放清单/values，ArgoCD 盯它；CI 把 tag 写到这个仓；
  - bot commit 落 config 仓，成为**"何时部署了哪个版本"的部署审计流水**，不算污染。
- 或用 **ArgoCD Image Updater**：盯镜像仓库新 tag，可配成改 Application 注解而**不产生 git commit**。
- 能说出这个取舍 = 懂 GitOps 的工程权衡。
